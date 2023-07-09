package ru.andver00.Service;

import org.telegram.telegrambots.meta.api.objects.User;
import ru.andver00.Entity.AppUser;
import ru.andver00.Entity.HotelRoom;
import ru.andver00.Enum.UserState;

public interface IMainService {
    AppUser findOrSaveAppUser(User telegramUser);
    String getCurrentUserState(User telegramUser);
    void setCurrentUserState(User telegramUser, UserState userState);
    public void setRoomForAppUser(User telegramUser, Long number);
    void saveNewHotelRoom(HotelRoom hotelRoom);
    HotelRoom getHotelRoomById(Long id);
}
