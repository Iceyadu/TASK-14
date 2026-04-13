package com.eaglepoint.exam.rooms.controller;

import com.eaglepoint.exam.rooms.model.Campus;
import com.eaglepoint.exam.rooms.model.Room;
import com.eaglepoint.exam.rooms.service.RoomService;
import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.enums.Permission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for campus and room management.
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // ---- Campus endpoints ----

    @GetMapping("/campuses")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<List<Campus>>> listCampuses() {
        List<Campus> campuses = roomService.listCampuses();
        return ResponseEntity.ok(ApiResponse.success(campuses));
    }

    @GetMapping("/campuses/{id}")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<Campus>> getCampus(@PathVariable Long id) {
        Campus campus = roomService.getCampus(id);
        return ResponseEntity.ok(ApiResponse.success(campus));
    }

    @PostMapping("/campuses")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<Campus>> createCampus(@RequestBody Campus campus) {
        Campus created = roomService.createCampus(campus);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    @PutMapping("/campuses/{id}")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<Campus>> updateCampus(@PathVariable Long id, @RequestBody Campus campus) {
        Campus updated = roomService.updateCampus(id, campus);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/campuses/{id}")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<Void>> deleteCampus(@PathVariable Long id) {
        roomService.deleteCampus(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ---- Room endpoints ----

    @GetMapping("/rooms")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<List<Room>>> listRooms(@RequestParam Long campusId) {
        List<Room> rooms = roomService.listRooms(campusId);
        return ResponseEntity.ok(ApiResponse.success(rooms));
    }

    @GetMapping("/rooms/{id}")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<Room>> getRoom(@PathVariable Long id) {
        Room room = roomService.getRoom(id);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @PostMapping("/rooms")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<Room>> createRoom(@RequestBody Room room) {
        Room created = roomService.createRoom(room);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    @PutMapping("/rooms/{id}")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<Room>> updateRoom(@PathVariable Long id, @RequestBody Room room) {
        Room updated = roomService.updateRoom(id, room);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/rooms/{id}")
    @RequirePermission(Permission.ROOM_MANAGE)
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
