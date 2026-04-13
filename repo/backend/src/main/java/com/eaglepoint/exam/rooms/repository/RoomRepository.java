package com.eaglepoint.exam.rooms.repository;

import com.eaglepoint.exam.rooms.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link Room} entities.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByCampusId(Long campusId);
}
