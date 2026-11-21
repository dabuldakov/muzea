package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import android.view.ViewGroup
import com.example.muzea.databinding.BottomSheetAttachmentsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Компактный пикер вложений: ряд иконок поверх чата, без подписей и кнопки
 * «Отмена». Иконка сразу открывает системный выбор файлов.
 */
class AttachmentsBottomSheet : BottomSheetDialogFragment() {

    enum class Kind { PHOTO, VIDEO, FILE }

    interface Listener {
        fun onAttachmentKind(kind: Kind)
    }

    private var _binding: BottomSheetAttachmentsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAttachmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Иконки без подписей: долгое нажатие показывает подсказку.
        TooltipCompat.setTooltipText(binding.btnPhoto, getString(com.example.muzea.R.string.attach_photo))
        TooltipCompat.setTooltipText(binding.btnVideo, getString(com.example.muzea.R.string.attach_video))
        TooltipCompat.setTooltipText(binding.btnFile, getString(com.example.muzea.R.string.attach_file))

        binding.btnPhoto.setOnClickListener { pick(Kind.PHOTO) }
        binding.btnVideo.setOnClickListener { pick(Kind.VIDEO) }
        // Один пикер на аудио, видео и документы из хранилища телефона.
        binding.btnFile.setOnClickListener { pick(Kind.FILE) }
    }

    private fun pick(kind: Kind) {
        (parentFragment as? Listener)?.onAttachmentKind(kind)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): AttachmentsBottomSheet = AttachmentsBottomSheet()
    }
}
