package ru.andver00.Dao;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andver00.Entity.HotelRoom;

public interface HotelRoomDAO extends JpaRepository<HotelRoom, Long> {
}
