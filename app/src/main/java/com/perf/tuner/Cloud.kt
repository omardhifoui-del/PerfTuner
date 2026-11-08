package com.perf.tuner

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore

/** حفظ واسترجاع إعداداتك من حسابك (Firestore). القيم الأصلية الخاصة بكل هاتف لا تُرفع. */
object Cloud {
    private fun doc() = FirebaseFirestore.getInstance().collection("users")
        .document(FirebaseAuth.getInstance().currentUser!!.uid).collection("data").document("prefs")

    fun errorText(e: Exception?): String = when (e) {
        is FirebaseNetworkException -> "لا يوجد اتصال بالإنترنت"
        is FirebaseAuthWeakPasswordException -> "كلمة المرور ضعيفة (6 أحرف على الأقل)"
        is FirebaseAuthUserCollisionException -> "هذا البريد مسجّل من قبل، جرّب تسجيل الدخول"
        is FirebaseAuthInvalidUserException -> "لا يوجد حساب بهذا البريد"
        is FirebaseAuthInvalidCredentialsException -> "البريد أو كلمة المرور غير صحيحة"
        else -> e?.localizedMessage ?: "حدث خطأ غير متوقع"
    }

    class Snapshot(val pkgs: String, val on: List<String>, val choices: Map<String, String>)

    fun save(engine: Engine, done: (String) -> Unit) {
        if (FirebaseAuth.getInstance().currentUser == null) { done("سجّل الدخول أولاً"); return }
        val on = engine.onIds()
        val data = hashMapOf<String, Any>(
            "pkgs" to engine.pkgs().joinToString(","),
            "on" to on,
            "choices" to engine.choiceMap(),
            "ts" to System.currentTimeMillis()
        )
        doc().set(data)
            .addOnSuccessListener {
                runCatching {
                    val sp = FirebaseApp.getInstance().applicationContext.getSharedPreferences("stats", 0)
                    sp.edit().putLong("last_save", System.currentTimeMillis()).putInt("saves", sp.getInt("saves", 0) + 1).apply()
                }
                done("✔ حُفظت إعداداتك في حسابك (${on.size} تفعيلة)")
            }
            .addOnFailureListener { done("✘ " + errorText(it)) }
    }

    fun load(onResult: (Snapshot?, String) -> Unit) {
        if (FirebaseAuth.getInstance().currentUser == null) { onResult(null, "سجّل الدخول أولاً"); return }
        doc().get().addOnSuccessListener { s ->
            if (!s.exists()) { onResult(null, "لا توجد إعدادات محفوظة في هذا الحساب بعد"); return@addOnSuccessListener }
            val on = (s.get("on") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val ch = (s.get("choices") as? Map<*, *>)?.entries
                ?.mapNotNull { e -> val k = e.key as? String; val v = e.value as? String; if (k != null && v != null) k to v else null }
                ?.toMap() ?: emptyMap()
            onResult(Snapshot(s.getString("pkgs") ?: "", on, ch), "")
        }.addOnFailureListener { onResult(null, "✘ " + errorText(it)) }
    }

    /** يطبّق اللقطة على هذا الهاتف؛ استدعِه من خيط الخلفية. يعيد عدد ما طُبّق. */
    fun restore(engine: Engine, snap: Snapshot): Int {
        engine.setPkgs(snap.pkgs)
        var n = 0
        for (id in snap.on) {
            val parts = id.split('-')
            val p = parts.getOrNull(0)?.toIntOrNull() ?: continue
            val i = parts.getOrNull(1)?.toIntOrNull() ?: continue
            val t = TWEAKS[p]?.getOrNull(i) ?: continue
            if (t.oneShot || t.report || engine.isOn(id)) continue
            if (engine.apply(t, id)) n++
        }
        val all = CHOICES.values.flatten()
        for ((title, label) in snap.choices) {
            val ch = all.firstOrNull { it.title == title } ?: continue
            val cmd = ch.opts.firstOrNull { it.first == label }?.second ?: continue
            if (engine.runCmd(cmd)) { engine.setChoiceSel(title, label); n++ }
        }
        return n
    }
}
