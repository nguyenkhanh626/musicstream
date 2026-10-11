package com.example.musicstream.service;

import com.example.musicstream.dto.RoomActionMessage;
import com.example.musicstream.dto.RoomMemberDto;
import com.example.musicstream.dto.RoomStateDto;
import com.example.musicstream.model.Song;
import com.example.musicstream.model.User;
import com.example.musicstream.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncRoomServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SongService songService;

    private SyncRoomService syncRoomService;

    private User hostUser;
    private User guestUser;

    @BeforeEach
    void setUp() {
        syncRoomService = new SyncRoomService(userRepository, songService, messagingTemplate);

        hostUser = new User();
        hostUser.setId(1L);
        hostUser.setUsername("hostuser");
        hostUser.setDisplayName("Host User");

        guestUser = new User();
        guestUser.setId(2L);
        guestUser.setUsername("guestuser");
        guestUser.setDisplayName("Guest User");
    }

    @Test
    void testCreateRoom_GeneratesValidRoomAndCode() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Test", hostUser);

        assertNotNull(room);
        assertNotNull(room.getRoomId());
        assertNotNull(room.getInviteCode());
        assertEquals(6, room.getInviteCode().length());
        assertEquals("Phòng Test", room.getName());
        assertEquals(hostUser.getId(), room.getHostId());
        assertEquals(1, room.getMembers().size());
        assertEquals("hostuser", room.getMembers().get(0).getUsername());

        // Verify state retrieval
        RoomStateDto fetched = syncRoomService.getRoomState(room.getRoomId(), hostUser);
        assertNotNull(fetched);
        assertEquals(room.getRoomId(), fetched.getRoomId());
    }

    @Test
    void testJoinRoomByCode_Success() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Test", hostUser);

        RoomStateDto joinedRoom = syncRoomService.joinRoomByCode(room.getInviteCode(), guestUser);

        assertNotNull(joinedRoom);
        assertEquals(2, joinedRoom.getMembers().size());
        assertTrue(joinedRoom.getMembers().stream().anyMatch(m -> m.getUserId().equals(guestUser.getId())));

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/room/" + room.getRoomId()), any(RoomActionMessage.class));
    }

    @Test
    void testAddMemberByUsername_Success() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Test", hostUser);

        when(userRepository.findByUsername("guestuser")).thenReturn(Optional.of(guestUser));

        RoomMemberDto added = syncRoomService.addMemberByUsername(room.getRoomId(), "guestuser", hostUser);

        assertNotNull(added);
        assertEquals(guestUser.getId(), added.getUserId());

        RoomStateDto updatedRoom = syncRoomService.getRoomState(room.getRoomId(), hostUser);
        assertEquals(2, updatedRoom.getMembers().size());
    }

    @Test
    void testLeaveRoom_HostLeaves_SuspendsAndClosesRoom() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Test", hostUser);
        syncRoomService.joinRoomByCode(room.getInviteCode(), guestUser);

        // Host leaves -> Room must be closed and suspended
        syncRoomService.leaveRoom(room.getRoomId(), hostUser);

        assertThrows(IllegalArgumentException.class, () -> syncRoomService.getRoomState(room.getRoomId(), hostUser));
        assertThrows(IllegalArgumentException.class, () -> syncRoomService.joinRoomByCode(room.getInviteCode(), guestUser));
    }

    @Test
    void testLeaveRoom_AllMembersLeave_SuspendsRoom() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Test", hostUser);

        // Host is only member, host leaves -> Room closed
        syncRoomService.leaveRoom(room.getRoomId(), hostUser);

        assertThrows(IllegalArgumentException.class, () -> syncRoomService.getRoomState(room.getRoomId(), hostUser));
    }

    @Test
    void testExplicitCloseRoom_ByHost() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Test", hostUser);

        syncRoomService.closeRoom(room.getRoomId(), hostUser);

        assertThrows(IllegalArgumentException.class, () -> syncRoomService.getRoomState(room.getRoomId(), hostUser));
    }

    @Test
    void testHandleRoomAction_SyncsPlaybackState() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Test", hostUser);

        Song testSong = Song.builder()
                .id(100L)
                .title("Bài hát mới")
                .artist("Ca sĩ A")
                .build();
        when(songService.getSongById(100L)).thenReturn(testSong);

        RoomActionMessage action = RoomActionMessage.builder()
                .roomId(room.getRoomId())
                .type("CHANGE_SONG")
                .songId(100L)
                .currentTime(0.0)
                .isPlaying(true)
                .build();

        syncRoomService.handleRoomAction(action, hostUser);

        RoomStateDto state = syncRoomService.getRoomState(room.getRoomId(), hostUser);
        assertEquals(100L, state.getCurrentSongId());
        assertEquals("Bài hát mới", state.getCurrentSongTitle());
        assertEquals("Ca sĩ A", state.getCurrentSongArtist());
        assertTrue(state.getIsPlaying());

        // Verify broadcast
        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + room.getRoomId()), eq(action));
    }

    @Test
    void testGetActiveRoomForUser_RestoresRoomState() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Reload", hostUser);

        RoomStateDto activeRoom = syncRoomService.getActiveRoomForUser(hostUser);
        assertNotNull(activeRoom);
        assertEquals(room.getRoomId(), activeRoom.getRoomId());

        // Guest not in room yet
        assertNull(syncRoomService.getActiveRoomForUser(guestUser));

        // Guest joins
        syncRoomService.joinRoomByCode(room.getInviteCode(), guestUser);
        RoomStateDto guestActiveRoom = syncRoomService.getActiveRoomForUser(guestUser);
        assertNotNull(guestActiveRoom);
        assertEquals(room.getRoomId(), guestActiveRoom.getRoomId());
    }

    @Test
    void testIsUserInRoomWithSong_AuthorizesGuestStream() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Stream", hostUser);
        syncRoomService.joinRoomByCode(room.getInviteCode(), guestUser);

        Song hostSong = Song.builder()
                .id(999L)
                .title("Nhạc của Host")
                .uploader(hostUser)
                .build();
        when(songService.getSongById(999L)).thenReturn(hostSong);

        // Guest is authorized to stream host's song because guest is in the same room
        assertTrue(syncRoomService.isUserInRoomWithSong(guestUser.getId(), 999L));
    }

    @Test
    void testHandleRoomAction_ChatPreservesMemberIdentityAndPlaybackTiming() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Chat Test", hostUser);
        syncRoomService.joinRoomByCode(room.getInviteCode(), guestUser);

        // Host phát bài hát ở giây 20
        RoomActionMessage playAction = RoomActionMessage.builder()
                .roomId(room.getRoomId())
                .type("PLAY")
                .currentTime(20.0)
                .isPlaying(true)
                .build();
        syncRoomService.handleRoomAction(playAction, hostUser);

        // Guest gửi tin nhắn chat
        RoomActionMessage chatAction = RoomActionMessage.builder()
                .roomId(room.getRoomId())
                .type("CHAT")
                .text("Xin chào cả phòng!")
                .build();
        RoomActionMessage result = syncRoomService.handleRoomAction(chatAction, guestUser);

        // Người gửi phải là guestUser, không được biến thành hostUser
        assertEquals(guestUser.getUsername(), result.getSenderUsername());
        assertEquals(guestUser.getDisplayName(), result.getSenderDisplayName());

        // Kiểm tra thời gian phát vẫn tiếp tục từ 20.0s trở lên (không bị reset về 0)
        RoomStateDto stateAfterChat = syncRoomService.getRoomState(room.getRoomId(), hostUser);
        assertTrue(stateAfterChat.getCurrentTime() >= 20.0);
    }

    @Test
    void testHandleChatActionWithoutUser_PreservesSenderInfo() {
        RoomStateDto room = syncRoomService.createRoom("Phòng Chat Fallback", hostUser);

        RoomActionMessage anonChat = RoomActionMessage.builder()
                .roomId(room.getRoomId())
                .type("CHAT")
                .senderUsername("member_client")
                .senderDisplayName("Khách 123")
                .text("Tin nhắn từ khách")
                .build();

        RoomActionMessage broadcastMsg = syncRoomService.handleChatActionWithoutUser(anonChat);
        assertEquals("member_client", broadcastMsg.getSenderUsername());
        assertEquals("Khách 123", broadcastMsg.getSenderDisplayName());
        assertEquals("Tin nhắn từ khách", broadcastMsg.getText());
        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + room.getRoomId()), eq(anonChat));
    }
}
