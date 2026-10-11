package com.example.musicstream.service;

import com.example.musicstream.dto.RoomActionMessage;
import com.example.musicstream.dto.RoomMemberDto;
import com.example.musicstream.dto.RoomStateDto;
import com.example.musicstream.model.Song;
import com.example.musicstream.model.User;
import com.example.musicstream.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SyncRoomService {

    private final UserRepository userRepository;
    private final SongService songService;
    private final SimpMessagingTemplate messagingTemplate;

    // Quản lý phòng trong RAM: RoomId -> RoomState
    private final Map<String, RoomInstance> activeRooms = new ConcurrentHashMap<>();
    // Tra cứu mã mời: InviteCode -> RoomId
    private final Map<String, String> inviteCodeMap = new ConcurrentHashMap<>();

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public SyncRoomService(UserRepository userRepository,
                           SongService songService,
                           SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.songService = songService;
        this.messagingTemplate = messagingTemplate;
    }

    public static class RoomInstance {
        public String roomId;
        public String roomName;
        public String inviteCode;
        public Long hostId;
        public String hostUsername;
        public String hostDisplayName;

        public Long currentSongId;
        public Song currentSong;
        public Double currentTime = 0.0;
        public Boolean isPlaying = false;
        public Long lastActionTimestamp = System.currentTimeMillis();

        // Danh sách thành viên trong phòng (UserId -> RoomMemberDto)
        public Map<Long, RoomMemberDto> members = new ConcurrentHashMap<>();

        public boolean isActive = true;
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
            }
            code = sb.toString();
        } while (inviteCodeMap.containsKey(code));
        return code;
    }

    // ================= TẠO PHÒNG =================
    public RoomStateDto createRoom(String roomName, User host) {
        if (host == null) throw new AccessDeniedException("Yêu cầu đăng nhập");

        // Nếu host đang ở phòng khác, rời phòng đó trước
        leaveAnyExistingRoom(host);

        String roomId = UUID.randomUUID().toString().substring(0, 8);
        String inviteCode = generateUniqueInviteCode();

        RoomInstance room = new RoomInstance();
        room.roomId = roomId;
        room.roomName = (roomName != null && !roomName.isBlank()) ? roomName.trim() : "Phòng của " + (host.getDisplayName() != null ? host.getDisplayName() : host.getUsername());
        room.inviteCode = inviteCode;
        room.hostId = host.getId();
        room.hostUsername = host.getUsername();
        room.hostDisplayName = host.getDisplayName() != null ? host.getDisplayName() : host.getUsername();

        // Thêm host làm thành viên đầu tiên
        RoomMemberDto hostMember = RoomMemberDto.builder()
                .userId(host.getId())
                .username(host.getUsername())
                .displayName(room.hostDisplayName)
                .isHost(true)
                .build();
        room.members.put(host.getId(), hostMember);

        activeRooms.put(roomId, room);
        inviteCodeMap.put(inviteCode, roomId);

        return toDto(room);
    }

    // ================= VÀO PHÒNG BẰNG MÃ MỜI HOẶC ROOM ID =================
    public RoomStateDto joinRoomByCode(String codeOrId, User user) {
        if (user == null) throw new AccessDeniedException("Yêu cầu đăng nhập");

        String normalized = codeOrId.trim().toUpperCase();
        String roomId = inviteCodeMap.get(normalized);
        if (roomId == null) {
            // Thử tìm theo roomId trực tiếp
            roomId = codeOrId.trim();
        }

        RoomInstance room = activeRooms.get(roomId);
        if (room == null || !room.isActive) {
            throw new IllegalArgumentException("Phòng không tồn tại hoặc phiên nghe đã kết thúc");
        }

        // Rời phòng cũ nếu đang tham gia
        leaveAnyExistingRoom(user);

        boolean isHost = room.hostId.equals(user.getId());
        RoomMemberDto member = RoomMemberDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .isHost(isHost)
                .build();
        room.members.put(user.getId(), member);

        // Broadcast thông báo có người tham gia vào phòng
        RoomActionMessage joinMsg = RoomActionMessage.builder()
                .type("MEMBER_JOINED")
                .roomId(room.roomId)
                .senderUsername(user.getUsername())
                .senderDisplayName(member.getDisplayName())
                .timestamp(System.currentTimeMillis())
                .build();
        messagingTemplate.convertAndSend("/topic/room/" + room.roomId, joinMsg);

        return toDto(room);
    }

    // ================= THÊM NGƯỜI DÙNG BẰNG USERNAME =================
    public RoomMemberDto addMemberByUsername(String roomId, String targetUsername, User currentUser) {
        RoomInstance room = getRoomOrThrow(roomId);
        if (!room.hostId.equals(currentUser.getId())) {
            throw new AccessDeniedException("Chỉ chủ phòng mới có quyền mời trực tiếp thành viên");
        }

        User targetUser = userRepository.findByUsername(targetUsername.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng có tên: " + targetUsername));

        RoomMemberDto member = RoomMemberDto.builder()
                .userId(targetUser.getId())
                .username(targetUser.getUsername())
                .displayName(targetUser.getDisplayName() != null ? targetUser.getDisplayName() : targetUser.getUsername())
                .isHost(false)
                .build();
        room.members.put(targetUser.getId(), member);

        RoomActionMessage inviteMsg = RoomActionMessage.builder()
                .type("MEMBER_INVITED")
                .roomId(room.roomId)
                .senderUsername(targetUser.getUsername())
                .senderDisplayName(member.getDisplayName())
                .timestamp(System.currentTimeMillis())
                .build();
        messagingTemplate.convertAndSend("/topic/room/" + room.roomId, inviteMsg);

        return member;
    }

    // ================= RỜI PHÒNG =================
    public void leaveRoom(String roomId, User user) {
        RoomInstance room = activeRooms.get(roomId);
        if (room == null) return;

        room.members.remove(user.getId());

        // Báo cho những người còn lại
        RoomActionMessage leaveMsg = RoomActionMessage.builder()
                .type("MEMBER_LEFT")
                .roomId(roomId)
                .senderUsername(user.getUsername())
                .senderDisplayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .timestamp(System.currentTimeMillis())
                .build();
        messagingTemplate.convertAndSend("/topic/room/" + roomId, leaveMsg);

        // Quy tắc: Nếu chủ phòng rời đi hoặc mọi người đã ra hết -> ĐÌNH CHỈ PHÒNG
        if (room.hostId.equals(user.getId()) || room.members.isEmpty()) {
            closeRoom(roomId, user);
        }
    }

    // ================= ĐÓNG / ĐÌNH CHỈ PHÒNG =================
    public void closeRoom(String roomId, User requester) {
        RoomInstance room = activeRooms.get(roomId);
        if (room == null) return;

        // Đánh dấu phòng bị đình chỉ
        room.isActive = false;
        activeRooms.remove(roomId);
        inviteCodeMap.remove(room.inviteCode);

        // Thông báo đóng phòng tới toàn bộ client
        RoomActionMessage closeMsg = RoomActionMessage.builder()
                .type("ROOM_CLOSED")
                .roomId(roomId)
                .text("Phiên phòng nghe chung đã kết thúc bởi chủ phòng hoặc tất cả người dùng đã rời đi.")
                .timestamp(System.currentTimeMillis())
                .build();
        messagingTemplate.convertAndSend("/topic/room/" + roomId, closeMsg);
    }

    // ================= XỬ LÝ HÀNH ĐỘNG ĐỒNG BỘ TỪ WEBSOCKET =================
    public RoomActionMessage handleRoomAction(RoomActionMessage action, User sender) {
        RoomInstance room = getRoomOrThrow(action.getRoomId());

        // Cập nhật sender nếu có
        if (sender != null) {
            action.setSenderUsername(sender.getUsername());
            action.setSenderDisplayName(sender.getDisplayName() != null ? sender.getDisplayName() : sender.getUsername());
        }
        action.setTimestamp(System.currentTimeMillis());

        boolean isHost = sender != null && room.hostId.equals(sender.getId());

        switch (action.getType()) {
            case "PLAY":
                if (isHost) {
                    room.isPlaying = true;
                    if (action.getSongId() != null) {
                        room.currentSongId = action.getSongId();
                        try {
                            room.currentSong = songService.getSongById(action.getSongId());
                        } catch (Exception ignored) {}
                    }
                    if (action.getCurrentTime() != null) {
                        room.currentTime = action.getCurrentTime();
                    }
                    room.lastActionTimestamp = System.currentTimeMillis();
                }
                break;
            case "PAUSE":
                if (isHost) {
                    room.isPlaying = false;
                    if (action.getCurrentTime() != null) {
                        room.currentTime = action.getCurrentTime();
                    }
                    room.lastActionTimestamp = System.currentTimeMillis();
                }
                break;
            case "SEEK":
                if (isHost) {
                    if (action.getCurrentTime() != null) {
                        room.currentTime = action.getCurrentTime();
                    }
                    room.lastActionTimestamp = System.currentTimeMillis();
                }
                break;
            case "CHANGE_SONG":
                if (isHost && action.getSongId() != null) {
                    room.currentSongId = action.getSongId();
                    try {
                        room.currentSong = songService.getSongById(action.getSongId());
                        if (room.currentSong != null) {
                            action.setSongTitle(room.currentSong.getTitle());
                            action.setSongArtist(room.currentSong.getArtist());
                        }
                    } catch (Exception ignored) {}
                    room.currentTime = 0.0;
                    room.isPlaying = true;
                    room.lastActionTimestamp = System.currentTimeMillis();
                }
                break;
            case "TIME_UPDATE":
                // Định kỳ phát từ chủ phòng để giữ các máy khách đồng bộ thời gian thực
                if (isHost) {
                    if (action.getCurrentTime() != null) {
                        room.currentTime = action.getCurrentTime();
                    }
                    if (action.getIsPlaying() != null) {
                        room.isPlaying = action.getIsPlaying();
                    }
                    room.lastActionTimestamp = System.currentTimeMillis();
                }
                break;
            case "SYNC_STATE":
                if (isHost) {
                    if (action.getCurrentTime() != null) {
                        room.currentTime = action.getCurrentTime();
                    }
                    if (action.getIsPlaying() != null) {
                        room.isPlaying = action.getIsPlaying();
                    }
                    room.lastActionTimestamp = System.currentTimeMillis();
                }
                break;
            case "SYNC_REQUEST":
                // Thành viên mới vào hoặc reload xin thông tin bài hát và giây hiện tại
                action.setType("SYNC_STATE");
                action.setSongId(room.currentSongId);
                if (room.currentSong != null) {
                    action.setSongTitle(room.currentSong.getTitle());
                    action.setSongArtist(room.currentSong.getArtist());
                }
                action.setCurrentTime(getCurrentPlaybackTime(room));
                action.setIsPlaying(room.isPlaying);
                break;
            case "CHAT":
                // Tin nhắn trò chuyện giữa các thành viên - giữ nguyên thời gian phát
                break;
        }

        // Broadcast tới mọi người trong phòng
        messagingTemplate.convertAndSend("/topic/room/" + room.roomId, action);
        return action;
    }

    public RoomActionMessage handleChatActionWithoutUser(RoomActionMessage action) {
        RoomInstance room = getRoomOrThrow(action.getRoomId());
        action.setType("CHAT");
        if (action.getSenderDisplayName() == null || action.getSenderDisplayName().isBlank()) {
            action.setSenderDisplayName(action.getSenderUsername() != null ? action.getSenderUsername() : "Thành viên");
        }
        action.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/room/" + room.roomId, action);
        return action;
    }

    public RoomStateDto getRoomState(String roomId, User user) {
        RoomInstance room = getRoomOrThrow(roomId);
        if (!room.members.containsKey(user.getId())) {
            throw new AccessDeniedException("Bạn chưa tham gia phòng này");
        }
        return toDto(room);
    }

    public RoomStateDto getRoomStateById(String roomId) {
        RoomInstance room = activeRooms.get(roomId);
        if (room == null || !room.isActive) return null;
        return toDto(room);
    }

    public RoomStateDto getActiveRoomForUser(User user) {
        if (user == null) return null;
        for (RoomInstance room : activeRooms.values()) {
            if (room.isActive && room.members.containsKey(user.getId())) {
                return toDto(room);
            }
        }
        return null;
    }

    public boolean isUserInRoomWithSong(Long userId, Long songId) {
        // Kiểm tra xem bài hát này có đang được phát trong phòng mà user tham gia không
        for (RoomInstance room : activeRooms.values()) {
            if (room.isActive && room.members.containsKey(userId)) {
                if (room.currentSongId != null && room.currentSongId.equals(songId)) {
                    return true;
                }
                // Cho phép nghe nếu bài hát thuộc quyền sở hữu của chủ phòng (Host)
                try {
                    Song song = songService.getSongById(songId);
                    if (song != null && song.getUploader() != null && song.getUploader().getId().equals(room.hostId)) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    private void leaveAnyExistingRoom(User user) {
        for (RoomInstance room : new ArrayList<>(activeRooms.values())) {
            if (room.members.containsKey(user.getId())) {
                leaveRoom(room.roomId, user);
            }
        }
    }

    private RoomInstance getRoomOrThrow(String roomId) {
        RoomInstance room = activeRooms.get(roomId);
        if (room == null || !room.isActive) {
            throw new IllegalArgumentException("Phòng không tồn tại hoặc đã bị đình chỉ");
        }
        return room;
    }

    public double getCurrentPlaybackTime(RoomInstance room) {
        double time = room.currentTime != null ? room.currentTime : 0.0;
        if (Boolean.TRUE.equals(room.isPlaying) && room.lastActionTimestamp != null) {
            double elapsed = (System.currentTimeMillis() - room.lastActionTimestamp) / 1000.0;
            time += Math.max(0.0, elapsed);
        }
        return time;
    }

    private RoomStateDto toDto(RoomInstance room) {
        // Tính toán độ trôi thời gian thực nếu bài đang phát
        double time = getCurrentPlaybackTime(room);

        return RoomStateDto.builder()
                .roomId(room.roomId)
                .roomName(room.roomName)
                .inviteCode(room.inviteCode)
                .hostId(room.hostId)
                .hostName(room.hostDisplayName)
                .currentSongId(room.currentSongId)
                .currentSong(room.currentSong)
                .currentTime(time)
                .isPlaying(room.isPlaying)
                .lastActionTimestamp(room.lastActionTimestamp)
                .members(new ArrayList<>(room.members.values()))
                .active(room.isActive)
                .build();
    }
}

