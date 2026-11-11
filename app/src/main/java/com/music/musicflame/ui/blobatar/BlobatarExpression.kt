/*
 * Port a Kotlin de Blobatar (https://github.com/Alain00/blobatar), motor "generation-2",
 * versión de referencia 2.4.0. Basado en packages/blobatar/src/expression.ts y en el port a Dart.
 * Licencia MIT, Copyright (c) 2026 Alain. Texto completo en LICENSE.blobatar (esta carpeta).
 */
package com.music.musicflame.ui.blobatar

/**
 * Todos los canales que una expresión puede cambiar. Los canales con sufijo 2 son diferenciales: solo
 * afectan al segundo ojo.
 */
data class Pose(
    val esx: Double = 1.0,
    val esy: Double = 1.0,
    val tilt: Double = 0.0,
    val edy: Double = 0.0,
    val edx: Double = 0.0,
    val esx2: Double = 0.0,
    val esy2: Double = 0.0,
    val tilt2: Double = 0.0,
    val edy2: Double = 0.0,
    val lock: Double = 0.0,
    val heat: Double = 0.0,
    val shake: Double = 0.0,
    val rock: Double = 0.0,
    val bdy: Double = 0.0
) {
    fun toMap(): Map<String, Double> = linkedMapOf(
        "esx" to esx, "esy" to esy, "tilt" to tilt, "edy" to edy, "edx" to edx,
        "esx2" to esx2, "esy2" to esy2, "tilt2" to tilt2, "edy2" to edy2,
        "lock" to lock, "heat" to heat, "shake" to shake, "rock" to rock, "bdy" to bdy
    )
}

/** Una pose con nombre y, opcionalmente, el destino al que tiñe la paleta. */
data class Expression(val name: String, val pose: Pose, val tint: Tint? = null)

object BlobatarExpressions {
    val identityPose = Pose()

    val idle = Expression("idle", identityPose)
    val happy = Expression(
        "happy",
        Pose(esx = 1.72, esy = 0.3, tilt = 8.0, edy = -1.5, edx = 1.5, esx2 = 0.08, esy2 = 0.05, tilt2 = -16.0, lock = 1.0, bdy = -2.2)
    )
    val sad = Expression(
        "sad",
        Pose(esx = 0.6, esy = 0.56, tilt = 26.0, edy = 3.6, edx = 1.9, esx2 = -0.05, esy2 = -0.07, tilt2 = -7.0, lock = 1.0, bdy = 2.6)
    )
    val mad = Expression(
        "mad",
        Pose(esx = 1.85, esy = 0.26, tilt = -33.0, edy = 0.4, edx = 0.6, esy2 = -0.03, tilt2 = 5.0, lock = 1.0, heat = 0.62, shake = 0.55, bdy = 0.8),
        BlobatarColor.hot
    )
    val surprised = Expression(
        "surprised",
        Pose(esx = 1.34, esy = 1.2, tilt = -6.0, edy = -1.05, edx = 0.5, esx2 = 0.05, esy2 = 0.07, tilt2 = 3.0, lock = 1.0, bdy = -1.4)
    )
    val wink = Expression(
        "wink",
        Pose(esx = 1.32, esy = 0.76, tilt = 5.0, edy = -0.6, edx = 0.8, esx2 = 0.26, esy2 = -0.56, tilt2 = -11.0, lock = 1.0, bdy = -1.1)
    )
    val sleepy = Expression(
        "sleepy",
        Pose(esx = 1.14, esy = 0.22, edy = 2.4, edx = 0.3, esx2 = -0.04, esy2 = 0.03, tilt2 = 4.0, lock = 1.0, bdy = 1.2)
    )
    val smug = Expression(
        "smug",
        Pose(esx = 1.3, esy = 0.42, tilt = 18.0, edy = -0.5, edx = 0.5, esx2 = 0.06, esy2 = -0.06, tilt2 = -36.0, lock = 1.0, bdy = -1.0)
    )
    val unsure = Expression(
        "unsure",
        Pose(esx = 0.95, esy = 1.02, tilt = 4.0, edy = -0.2, edx = 0.3, esx2 = 0.24, esy2 = -0.44, tilt2 = -18.0, lock = 1.0)
    )
    val scared = Expression(
        "scared",
        Pose(esx = 0.78, esy = 0.96, tilt = -12.0, edy = -1.5, edx = -0.8, esx2 = -0.04, esy2 = 0.05, tilt2 = 4.0, lock = 1.0, shake = 0.35, bdy = -0.6)
    )
    val love = Expression(
        "love",
        Pose(esx = 0.86, esy = 1.28, tilt = -14.0, edy = -0.5, edx = -0.35, esx2 = 0.05, esy2 = 0.06, tilt2 = 6.0, lock = 1.0, heat = 0.6, bdy = -1.6),
        BlobatarColor.rose
    )
    val shy = Expression(
        "shy",
        Pose(esx = 0.62, esy = 0.5, tilt = 10.0, edy = 1.4, edx = -0.2, esx2 = -0.05, esy2 = -0.04, tilt2 = -8.0, lock = 1.0, heat = 0.55, bdy = 0.9),
        BlobatarColor.blush
    )
    val sick = Expression(
        "sick",
        Pose(esx = 1.25, esy = 0.34, tilt = 20.0, edy = 1.8, edx = 0.8, esx2 = 0.05, esy2 = -0.05, tilt2 = -6.0, lock = 1.0, heat = 0.6, shake = 0.18, bdy = 1.4),
        BlobatarColor.bile
    )
    val thinking = Expression(
        "thinking",
        Pose(esx = 1.15, esy = 0.62, edy = 4.2, edx = 0.4, esx2 = 0.02, esy2 = 0.06, edy2 = -8.4, lock = 1.0, rock = 0.8, bdy = -0.4)
    )

    /** Las 14 expresiones, en el orden del original. */
    val all: List<Expression> = listOf(idle, happy, sad, mad, surprised, wink, sleepy, smug, unsure, scared, love, shy, sick, thinking)

    val byName: Map<String, Expression> = all.associateBy { it.name }

    /** Hornea una pose estática en la geometría de los ojos y en el desplazamiento del cuerpo. */
    fun bakePose(layout: BlobatarLayout, pose: Pose): BlobatarLayout = BlobatarLayout(
        shape = layout.shape,
        body = layout.body,
        face = layout.face,
        eyes = layout.eyes.mapIndexed { i, e ->
            val second = i == 1
            val side = if (i == 0) -1.0 else 1.0
            Eye(
                e.cx + pose.edx * side,
                e.cy + pose.edy + (if (second) pose.edy2 else 0.0),
                e.rx * (pose.esx + (if (second) pose.esx2 else 0.0)),
                e.ry * (pose.esy + (if (second) pose.esy2 else 0.0)),
                e.n,
                e.rot * (1 - pose.lock) + (pose.tilt + (if (second) pose.tilt2 else 0.0)) * (if (i == 0) -1.0 else 1.0)
            )
        },
        petals = layout.petals,
        extra = layout.extra,
        draw = layout.draw,
        bodyOffsetY = pose.bdy
    )

    /** Resuelve una expresión que tiñe contra la paleta que de verdad lleva puesta. */
    fun expressionPalette(palette: Map<String, String>, expression: Expression): Map<String, String> {
        val target = expression.tint ?: return palette
        val head = palette.getValue(BlobatarColor.HEAD)
        val eye = palette.getValue(BlobatarColor.EYE)
        val (tintedHead, tintedEye) = BlobatarColor.tinted(head, eye, target)
        val out = LinkedHashMap(palette)
        out[BlobatarColor.HEAD] = BlobatarColor.mixHex(head, tintedHead, expression.pose.heat)
        out[BlobatarColor.EYE] = BlobatarColor.mixHex(eye, tintedEye, expression.pose.heat)
        return out
    }
}
