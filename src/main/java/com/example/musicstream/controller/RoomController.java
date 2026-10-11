package com.example.musicstream.controller;

import com.example.musicstream.dto.RoomActionMessage;
import com.example.musicstream.dto.RoomMemberDto;
import com.example.musicstream.dto.RoomStateDto;
import com.example.musicstream.model.User;
import com.example.musicstream.repository.UserRepository;
import com.example.musicstream.security.JwtTokenProvider;
import com.example.musicstream.security.UserPrincipal;
import com.example.musicstream.service.SyncRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final SyncRoomService syncRoomService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public RoomController(SyncRoomService syncRoomService, UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.syncRoomService = syncRoomService;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // 1. Tạo phòng nghe chung
    @PostMapping("/create")
    public ResponseEntity<RoomStateDto> createRoom(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String roomName = name;
        if ((roomName == null || roomName.isBlank()) && body != null) {
            roomName = body.get("name");
        }
        RoomStateDto room = syncRoomService.createRoom(roomName, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    // 2. Tham gia phòng bằng mã mời hoặc room ID
    @PostMapping("/join")
    public ResponseEntity<RoomStateDto> joinRoom(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String codeOrId,
            @RequestParam(required = false) String code,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String targetCode = (codeOrId != null && !codeOrId.isBlank()) ? codeOrId : code;
        if ((targetCode == null || targetCode.isBlank()) && body != null) {
            targetCode = body.get("codeOrId") != null ? body.get("codeOrId") : body.get("code");
        }
        if (targetCode == null || targetCode.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        RoomStateDto room = syncRoomService.joinRoomByCode(targetCode, principal.getUser());
        return ResponseEntity.ok(room);
    }

    // 3. Mời người dùng vào phòng theo username
    @PostMapping("/{roomId}/invite")
    public ResponseEntity<RoomMemberDto> inviteUser(
            @PathVariable String roomId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String username,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String targetUsername = username;
        if ((targetUsername == null || targetUsername.isBlank()) && body != null) {
            targetUsername = body.get("username");
        }
        if (targetUsername == null || targetUsername.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        RoomMemberDto member = syncRoomService.addMemberByUsername(roomId, targetUsername, principal.getUser());
        return ResponseEntity.ok(member);
    }

    // 4. Lấy trạng thái hiện tại của phòng
    @GetMapping("/{roomId}/state")
    public ResponseEntity<RoomStateDto> getRoomState(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(syncRoomService.getRoomState(roomId, principal.getUser()));
    }

    // 5. Rời phòng
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        syncRoomService.leaveRoom(roomId, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    // 6. Đóng / đình chỉ phòng (dành cho Host)
    @PostMapping("/{roomId}/close")
    public ResponseEntity<Void> closeRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        syncRoomService.closeRoom(roomId, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    // 7. Lấy phòng đang tham gia của người dùng hiện tại (hỗ trợ khôi phục khi reload trang)
    @GetMapping("/my-active-room")
    public ResponseEntity<RoomStateDto> getMyActiveRoom(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        RoomStateDto room = syncRoomService.getActiveRoomForUser(principal.getUser());
        if (room == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(room);
    }

    // ================= STOMP WEBSOCKET MESSAGE HANDLER =================
    @MessageMapping("/room/action")
    public void processRoomAction(
            @Payload RoomActionMessage action,
            Principal principal,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        User user = null;
        if (userPrincipal != null) {
            user = userPrincipal.getUser();
        } else if (principal instanceof Authentication auth && auth.getPrincipal() instanceof UserPrincipal up) {
            user = up.getUser();
        }

        // Ưu tiên 2: Giải mã JWT token gửi kèm trong payload action nếu có
        if (user == null && action.getToken() != null && !action.getToken().isBlank()) {
            String token = action.getToken().trim();
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            try {
                if (jwtTokenProvider.validateToken(token)) {
                    String username = jwtTokenProvider.getUsernameFromJWT(token);
                    user = userRepository.findByUsername(username).orElse(null);
                }
            } catch (Exception ignored) {}
        }

        // Ưu tiên 3: Tìm theo senderUsername nếu hợp lệ và không phải anonymous
        if (user == null && action.getSenderUsername() != null && !action.getSenderUsername().isBlank() && !"anonymous".equalsIgnoreCase(action.getSenderUsername())) {
            user = userRepository.findByUsername(action.getSenderUsername()).orElse(null);
        }

        if (user != null) {
            syncRoomService.handleRoomAction(action, user);
        } else {
            // TUYỆT ĐỐI KHÔNG FALLBACK SANG HOST CHO TIN NHẮN CHAT!
            if ("CHAT".equalsIgnoreCase(action.getType()) || "CHAT".equalsIgnoreCase(action.getActionType())) {
                syncRoomService.handleChatActionWithoutUser(action);
            } else {
                // Chỉ áp dụng fallback sang host cho hành động đồng bộ nhạc nếu không xác thực được
                RoomStateDto room = syncRoomService.getRoomStateById(action.getRoomId());
                if (room != null && room.getHostId() != null) {
                    user = userRepository.findById(room.getHostId()).orElse(null);
                    if (user != null) {
                        syncRoomService.handleRoomAction(action, user);
                    }
                }
            }
        }
    }
}
