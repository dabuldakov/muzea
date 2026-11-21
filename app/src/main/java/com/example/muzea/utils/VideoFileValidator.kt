package com.example.muzea.utils

import java.io.File
import java.io.RandomAccessFile

/**
 * Проверка целостности выбранного видео до загрузки.
 *
 * Провайдер контента (облачная галерея, кэш мессенджера) иногда отдаёт поток,
 * который заканчивается раньше времени: файл копируется «успешно», но в MinIO
 * уезжает половина ролика. Для MP4/MOV это видно по атомам: размер `mdat`
 * выходит за конец файла, и ExoPlayer падает на середине с
 * `ERROR_CODE_IO_UNSPECIFIED`.
 *
 * Для остальных контейнеров (WebM, MKV) проверка не выполняется — вернём false.
 */
object VideoFileValidator {

    /** true — файл заведомо обрезан и загружать его нельзя. */
    fun isTruncated(file: File): Boolean {
        if (file.length() < MIN_HEADER_BYTES) return true
        return runCatching { walkAtoms(file) }.getOrDefault(false)
    }

    private fun walkAtoms(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()

            // MP4/MOV начинается с ftyp. Иначе это другой контейнер — не проверяем.
            raf.seek(0)
            val firstSize = readSize(raf)
            if (readType(raf) != "ftyp" || firstSize < 8) return false

            var offset = 0L
            while (offset + 8 <= length) {
                raf.seek(offset)
                var size = readSize(raf)
                val type = readType(raf)
                if (!type.all { it in ' '..'~' }) return false

                size = when {
                    // Атом до конца файла — валидный вариант.
                    size == 0L -> return false
                    // 64-битный размер идёт после типа.
                    size == 1L -> raf.readLong()
                    size < 8L -> return true
                    else -> size
                }

                if (offset + size > length) return true
                offset += size
            }
            return false
        }
    }

    private fun readSize(raf: RandomAccessFile): Long =
        raf.readInt().toLong() and 0xFFFF_FFFFL

    private fun readType(raf: RandomAccessFile): String {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return bytes.toString(Charsets.ISO_8859_1)
    }

    private const val MIN_HEADER_BYTES = 16L
}
