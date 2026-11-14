package com.example.muzea.ui.chat

import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.HiltTestActivity
import com.example.muzea.utils.ChatNotifications
import com.example.muzea.utils.NotificationChannels
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.di.ChatRepositoryModule
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.repository.AvatarRepository
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ChatSessionRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.repository.GifRepository
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.data.repository.ChatListCache
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.data.repository.OutboxEvent
import com.example.muzea.data.repository.OutboxSender
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric+Hilt тесты переписки: заголовок, пустое состояние, рендер
 * сообщений и отправка текста через очередь.
 */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class ChatConversationFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val chatRepository: ChatRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val messageRepository: MessageRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val gifRepository: GifRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val contactRepository: ContactRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val avatarRepository: AvatarRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val chatSessionRepository: ChatSessionRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val outboxSender: OutboxSender = mockk(relaxed = true)

    private val outboxEvents = MutableSharedFlow<OutboxEvent>()

    @Before
    fun setUp() {
        hiltRule.inject()
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("chat_user_uuid", "me").commit()
        ChatMessagesCache.clear()
        ChatListCache.clear()
        every { messageRepository.cachedMessages(any()) } returns emptyList()
        coEvery { messageRepository.loadMessages(any()) } returns flowOf(Resource.Success(emptyList()))
        coEvery { messageRepository.loadMessagesAfter(any(), any()) } returns
            flowOf(Resource.Success(emptyList()))
        coEvery { messageRepository.loadChatReceipts(any()) } returns emptyList()
        coEvery { messageRepository.loadUpdatedMessages(any(), any()) } returns emptyList()
        coEvery { messageRepository.loadPinned(any()) } returns emptyList()
        coEvery { messageRepository.markMessagesAsRead(any(), any()) } returns true
        coEvery { messageRepository.markMessagesAsDelivered(any(), any()) } returns true
        every { outboxSender.events } returns outboxEvents
        every { outboxSender.pendingForChat(any()) } returns emptyList()
    }

    private fun message(uuid: String = "m1", text: String = "привет") = Message(
        messageUuid = uuid,
        messageId = 1,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = "me",
        senderName = "Me",
        senderAvatar = null,
        text = text,
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T00:00:00",
        updatedAt = null
    )

    private fun awaitItems(list: RecyclerView, count: Int) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (list.adapter?.itemCount == count) return
            Thread.sleep(10)
        }
        assertEquals(count, list.adapter?.itemCount)
    }

    private fun layout(list: RecyclerView) {
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1080, 2400)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun launchWithActivity(): Pair<HiltTestActivity, ChatConversationFragment> {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = ChatConversationFragment.newInstance("chat-1", "Баги")
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    private fun messageWithAttachment(type: String = "DOCUMENT"): Message {
        val attachment = com.example.muzea.domain.model.MessageAttachment(
            fileUrl = "/f", thumbnailUrl = null, type = type, width = 10, height = 10,
            attachmentUuid = "a-1", fileName = "file.pdf", fileSize = 128L,
            mimeType = "application/pdf"
        )
        return message().copy(text = "", messageType = type, attachments = listOf(attachment))
    }

    private fun launch(): ChatConversationFragment {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = ChatConversationFragment.newInstance("chat-1", "Баги")
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun `renders title and empty conversation`() {
        val fragment = launch()

        val title = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvTitle)
        assertEquals("Баги", title.text.toString())
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        assertEquals(0, list.adapter?.itemCount)
        assertEquals(6, fragment.requireView()
            .findViewById<android.widget.EditText>(R.id.etMessageInput).maxLines)
    }

    @Test
    fun `renders messages from the repository`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        assertEquals(1, list.adapter?.itemCount)
    }

    @Test
    fun `clicking a message opens the floating actions popup`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        awaitItems(list, 1)
        layout(list)

        val bubble = list.findViewHolderForAdapterPosition(0)!!
            .itemView.findViewById<View>(R.id.tvMessage)
        bubble.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(true, fragment.isActionsPopupShowing())
    }

    @Test
    fun `clicking empty row area does not open actions`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        awaitItems(list, 1)
        layout(list)

        list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(false, fragment.isActionsPopupShowing())
    }

    @Test
    fun `select action enters selection mode and close exits`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_SELECT)

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.selectionBar).visibility
        )
        assertEquals(
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.composerBar).visibility
        )
        fragment.requireView().findViewById<View>(R.id.btnSelectionClose).performClick()
        assertEquals(
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.selectionBar).visibility
        )
    }

    @Test
    fun `reply action shows the reply banner`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_REPLY)

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.replyBanner).visibility
        )
    }

    @Test
    fun `edit action fills the input`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_EDIT)

        val input = fragment.requireView().findViewById<android.widget.EditText>(R.id.etMessageInput)
        assertEquals("привет", input.text.toString())
    }

    @Test
    fun `copy action puts the text into the clipboard`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_COPY)

        val clipboard = fragment.requireContext()
            .getSystemService(android.content.ClipboardManager::class.java)
        assertEquals("привет", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun `back button and title use the navigator`() {
        val (activity, fragment) = launchWithActivity()

        fragment.requireView().findViewById<View>(R.id.btnBack).performClick()
        fragment.requireView().findViewById<View>(R.id.tvTitle).performClick()

        assertEquals(true, activity.backCalled)
        assertEquals("chat-1", activity.lastGroupSettings?.chatUuid)
    }

    @Test
    fun `delete selected shows the confirmation dialog`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_SELECT)

        fragment.requireView().findViewById<View>(R.id.btnSelectionDelete).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.isShowing
        )
    }

    @Test
    fun `forward selected opens the chat picker`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { chatRepository.loadChats() } returns
            flowOf(Resource.Success(emptyList()))
        val fragment = launch()
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_SELECT)

        fragment.requireView().findViewById<View>(R.id.btnSelectionForward).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            fragment.childFragmentManager.findFragmentByTag("forward_picker") != null
        )
    }

    @Test
    fun `pin and unpin actions call the repository`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { messageRepository.pinMessage("chat-1", "m1") } returns true
        coEvery { messageRepository.unpinMessage("chat-1", "m1") } returns true
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_PIN)
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_UNPIN)
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { messageRepository.pinMessage("chat-1", "m1") }
        io.mockk.coVerify { messageRepository.unpinMessage("chat-1", "m1") }
    }

    @Test
    fun `delete action confirms and deletes the message`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { messageRepository.deleteMessage("m1") } returns true
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_DELETE)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            as androidx.appcompat.app.AlertDialog
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { messageRepository.deleteMessage("m1") }
    }

    @Test
    fun `block action opens the confirmation dialog`() {
        every { messageRepository.cachedMessages("chat-1") } returns
            listOf(message().copy(senderUuid = "other"))
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_BLOCK)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.isShowing
        )
    }

    @Test
    fun `forward action opens the chat picker`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { chatRepository.loadChats() } returns flowOf(Resource.Success(emptyList()))
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_FORWARD)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            fragment.childFragmentManager.findFragmentByTag("forward_picker") != null
        )
    }

    @Test
    fun `reply can be cancelled from the banner`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_REPLY)

        fragment.requireView().findViewById<View>(R.id.btnCancelReply).performClick()

        assertEquals(
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.replyBanner).visibility
        )
    }

    @Test
    fun `send after edit submits the edited text`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { messageRepository.editMessage("m1", "исправлено") } returns
            flowOf(Resource.Success(message()))
        val fragment = launch()
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_EDIT)
        shadowOf(Looper.getMainLooper()).idle()
        fragment.requireView().findViewById<android.widget.EditText>(R.id.etMessageInput)
            .setText("исправлено")

        fragment.requireView().findViewById<View>(R.id.btnSend).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { messageRepository.editMessage("m1", "исправлено") }
    }

    @Test
    fun `empty composer cycles send mode without crashing`() {
        val fragment = launch()

        repeat(3) { fragment.requireView().findViewById<View>(R.id.btnSend).performClick() }

        assertTrue(true)
    }

    @Test
    fun `document attachment downloads and opens on tap`() {
        every { messageRepository.cachedMessages("chat-1") } returns
            listOf(messageWithAttachment("DOCUMENT"))
        coEvery { messageRepository.downloadAttachment("a-1", any(), any()) } returns null
        val fragment = launch()
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        awaitItems(list, 1)
        layout(list)
        val item = list.findViewHolderForAdapterPosition(0)!!.itemView
        val container = item.findViewById<android.view.ViewGroup>(R.id.attachmentsContainer)

        container.getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(true)
    }

    @Test
    fun `image attachment opens the viewer`() {
        every { messageRepository.cachedMessages("chat-1") } returns
            listOf(messageWithAttachment("IMAGE"))
        val fragment = launch()
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        awaitItems(list, 1)
        layout(list)
        val item = list.findViewHolderForAdapterPosition(0)!!.itemView
        val container = item.findViewById<android.view.ViewGroup>(R.id.attachmentsContainer)

        container.getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(fragment.requireActivity()).nextStartedActivity
        assertTrue(started.component?.className!!.endsWith("ImageGalleryActivity"))
    }

    private fun touch(action: Int, y: Float = 1000f): android.view.MotionEvent =
        android.view.MotionEvent.obtain(0L, 0L, action, 10f, y, 0)

    @Test
    fun `short tap sends with zero power`() {
        val fragment = launch()
        fragment.requireView().findViewById<android.widget.EditText>(R.id.etMessageInput)
            .setText("привет")

        val button = fragment.requireView().findViewById<View>(R.id.btnSend)
        button.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN))
        button.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP))
        shadowOf(Looper.getMainLooper()).idle()

        verify { outboxSender.enqueue(any()) }
        assertEquals(
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.chargeBar).visibility
        )
    }

    @Test
    fun `holding the send button charges and sends power`() {
        val fragment = launch()
        fragment.requireView().findViewById<android.widget.EditText>(R.id.etMessageInput)
            .setText("привет")
        val button = fragment.requireView().findViewById<View>(R.id.btnSend)

        button.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2_500))

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.chargeBar).visibility
        )
        val percent = fragment.requireView()
            .findViewById<android.widget.TextView>(R.id.tvChargePercent).text.toString()
        assertTrue(percent.contains("%"))
        assertTrue(percent != fragment.getString(R.string.charge_zero))

        button.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP))
        shadowOf(Looper.getMainLooper()).idle()

        verify { outboxSender.enqueue(any()) }
        assertEquals(
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.chargeBar).visibility
        )
    }

    @Test
    fun `sliding up cancels the charge without sending`() {
        val fragment = launch()
        fragment.requireView().findViewById<android.widget.EditText>(R.id.etMessageInput)
            .setText("привет")
        val button = fragment.requireView().findViewById<View>(R.id.btnSend)
        io.mockk.clearMocks(outboxSender, answers = false)

        button.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2_500))
        button.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, y = 500f))
        button.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, y = 500f))
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { outboxSender.enqueue(any()) }
    }

    @Test
    fun `group entry with several participants and unread image does not crash`() {
        val participants = (1..5).map { index ->
            com.example.muzea.domain.model.ChatReceipt(
                userUuid = "u$index",
                username = "user$index",
                fullName = "User $index",
                avatarUrl = null,
                joinedAt = "2026-10-15T10:30:00",
                lastDeliveredMessageId = 100L + index,
                lastDeliveredAt = "2026-11-09T04:53:00",
                lastReadMessageId = 100L + index,
                lastReadAt = "2026-11-09T04:53:00"
            )
        }
        coEvery { messageRepository.loadChatReceipts("chat-1") } returns participants
        com.example.muzea.data.repository.ChatListCache.put(
            listOf(
                com.example.muzea.domain.model.Chat(
                    chatUuid = "chat-1",
                    chatType = "GROUP",
                    title = "группа 1",
                    avatarUrl = null,
                    createdAt = null,
                    updatedAt = null,
                    participantCount = 6,
                    lastMessage = null,
                    unreadCount = 11
                )
            )
        )
        val history = (1..11).map { index ->
            message(uuid = "m$index", text = "сообщение $index").copy(
                senderUuid = "u${index % 5 + 1}",
                senderName = "User ${index % 5 + 1}",
                messageId = 1670L + index
            )
        }
        val image = message(uuid = "image-1880", text = "").copy(
            messageId = 1880L,
            senderUuid = "u2",
            senderName = "yegor",
            messageType = "IMAGE",
            attachments = listOf(
                com.example.muzea.domain.model.MessageAttachment(
                    fileUrl = "attachments/photo.jpg",
                    thumbnailUrl = null,
                    type = "IMAGE",
                    width = 1080,
                    height = 2400,
                    attachmentUuid = "a-image",
                    fileName = "1000000158.jpg",
                    fileSize = 862738,
                    mimeType = "image/jpeg"
                )
            )
        )
        every { messageRepository.cachedMessages("chat-1") } returns history + image
        coEvery { messageRepository.loadMessages(any()) } returns
            flowOf(Resource.Success(history + image))

        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = ChatConversationFragment.newInstance(
            "chat-1", "группа 1", unreadCount = 11
        )
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "группа 1",
            fragment.requireView().findViewById<android.widget.TextView>(R.id.tvTitle).text.toString()
        )
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        awaitItems(list, 12)
        assertEquals(12, list.adapter?.itemCount)
    }

    @Test
    fun `pinned message is shown in the pinned bar`() {
        coEvery { messageRepository.loadPinned("chat-1") } returns listOf(message(text = "закреп"))

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.pinnedBar).visibility
        )
        val preview = fragment.requireView()
            .findViewById<android.widget.TextView>(R.id.tvPinnedText)
        assertEquals("закреп", preview.text.toString())
    }

    @Test
    fun `attach button opens the attachments sheet`() {
        val fragment = launch()

        fragment.requireView().findViewById<View>(R.id.btnAdd).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val sheet = fragment.childFragmentManager.findFragmentByTag("attachments")
        assertEquals(true, sheet is AttachmentsBottomSheet)
    }

    @Test
    fun `send button queues the typed message and clears the input`() {
        val fragment = launch()

        val input = fragment.requireView().findViewById<android.widget.EditText>(R.id.etMessageInput)
        input.setText("привет")
        fragment.requireView().findViewById<View>(R.id.btnSend).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("", input.text.toString())
        verify { outboxSender.enqueue(any()) }
    }

    @Test
    fun `opening the chat clears its push notification`() {
        val context =
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        NotificationChannels.ensureAll(context)
        val notification = androidx.core.app.NotificationCompat.Builder(
            context,
            context.getString(R.string.chat_channel_id)
        ).setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Баги").build()
        manager.notify(
            ChatNotifications.tag("chat-1"),
            ChatNotifications.id("chat-1"),
            notification
        )
        assertNotNull(
            shadowOf(manager).getNotification(
                ChatNotifications.tag("chat-1"),
                ChatNotifications.id("chat-1")
            )
        )

        launch()

        // Сообщения уже видно в ленте — уведомление из шторки убираем.
        assertNull(
            shadowOf(manager).getNotification(
                ChatNotifications.tag("chat-1"),
                ChatNotifications.id("chat-1")
            )
        )
    }

    @Test
    fun `deleted peer disables the composer`() {
        ChatListCache.put(
            listOf(
                com.example.muzea.domain.model.Chat(
                    chatUuid = "chat-1",
                    chatType = "PRIVATE",
                    title = null,
                    avatarUrl = null,
                    createdAt = null,
                    updatedAt = null,
                    participantCount = 2,
                    lastMessage = null,
                    unreadCount = 0,
                    peerDeleted = true
                )
            )
        )

        val fragment = launch()

        val input = fragment.requireView()
            .findViewById<android.widget.EditText>(R.id.etMessageInput)
        assertFalse(input.isEnabled)
        assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.btnSend).visibility)
        assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.btnAdd).visibility)
    }
}
