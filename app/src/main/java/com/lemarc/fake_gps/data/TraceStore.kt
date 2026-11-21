package com.lemarc.fake_gps.data

import com.lemarc.fake_gps.data.gpx.GpxParser
import com.lemarc.fake_gps.data.gpx.GpxWriter
import com.lemarc.fake_gps.model.LatLon
import java.io.File
import java.io.IOException

/** Traces enregistrées dans le stockage privé de l'application, au format GPX. */
class TraceStore(baseDir: File) {

    data class Trace(val name: String, val file: File, val modified: Long)

    private val dir = File(baseDir, "traces")

    /** Traces enregistrées, la plus récente en premier. */
    fun list(): List<Trace> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) } ?: emptyArray())
            .map { Trace(it.name.removeSuffix(EXT), it, it.lastModified()) }
            .sortedByDescending { it.modified }

    fun exists(name: String) = fileFor(name).exists()

    /** Enregistre (ou remplace) la trace [name]. Lève [IOException] si l'écriture échoue. */
    fun save(name: String, points: List<LatLon>) {
        require(points.isNotEmpty()) { "Une trace doit contenir au moins un point" }
        dir.mkdirs()
        val target = fileFor(name)
        val tmp = File(dir, target.name + ".tmp")
        tmp.outputStream().use { GpxWriter.write(name, points, it) }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Impossible de renommer ${tmp.name} en ${target.name}")
        }
    }

    fun load(name: String): List<LatLon> = fileFor(name).inputStream().use { GpxParser.parse(it) }

    fun delete(name: String) = fileFor(name).delete()

    private fun fileFor(name: String) = File(dir, sanitize(name) + EXT)

    companion object {
        private const val EXT = ".gpx"

        /** Nom de fichier sûr : caractères interdits remplacés, longueur limitée. */
        fun sanitize(name: String): String {
            val cleaned = name.trim()
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                .trimStart('.')
                .take(80)
            return cleaned.ifEmpty { "trace" }
        }
    }
}
