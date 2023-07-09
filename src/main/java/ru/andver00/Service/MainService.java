package ru.andver00.Service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.andver00.Dao.AppUserDAO;
import ru.andver00.Dao.HotelRoomDAO;
import ru.andver00.Entity.AppUser;
import ru.andver00.Entity.HotelRoom;
import ru.andver00.Enum.UserState;

import static ru.andver00.Enum.UserState.BASIC_STATE;

@Service
public class MainService implements IMainService{
    private final AppUserDAO appUserDAO;
    private final HotelRoomDAO hotelRoomDAO;

    MainService(AppUserDAO appUserDAO, HotelRoomDAO hotelRoomDAO) {
        this.appUserDAO = appUserDAO;
        this.hotelRoomDAO = hotelRoomDAO;
    }

    public AppUser findOrSaveAppUser(User telegramUser) {
        AppUser persistentAppUser = appUserDAO.findAppUserByTelegramUserId(telegramUser.getId());
        if (persistentAppUser == null) {
            AppUser transientAppUser = AppUser.builder()
                    .telegramUserId(telegramUser.getId())
                    .firstName(telegramUser.getFirstName())
                    .lastName(telegramUser.getLastName())
                    .userName(telegramUser.getUserName())
                    .registered(true)
                    .userState(BASIC_STATE)
                    .currentRoomInListId(1L)
                    .build();

            return appUserDAO.save(transientAppUser);
        }

        return appUserDAO.save(persistentAppUser);
    }

    public String getCurrentUserState(User telegramUser) {
        AppUser persistentAppUser = appUserDAO.findAppUserByTelegramUserId(telegramUser.getId());
        if (persistentAppUser == null) {
            return null;
        }

        return String.valueOf(persistentAppUser.getUserState());
    }

    public void setCurrentUserState(User telegramUser, UserState userState) {
        AppUser persistentAppUser = appUserDAO.findAppUserByTelegramUserId(telegramUser.getId());
        persistentAppUser.setUserState(userState);
        appUserDAO.save(persistentAppUser);
    }

    public void setRoomForAppUser(User telegramUser, Long number) {
        AppUser persistentAppUser = appUserDAO.findAppUserByTelegramUserId(telegramUser.getId());
        var currentRoomId = persistentAppUser.getCurrentRoomInListId();
        if (hotelRoomDAO.findById(currentRoomId + number).isPresent()) {
            persistentAppUser.setCurrentRoomInListId(currentRoomId + number);
            appUserDAO.save(persistentAppUser);        }
    }

    public void saveNewHotelRoom(HotelRoom hotelRoom) {
        hotelRoomDAO.save(hotelRoom);
    }

    public HotelRoom getHotelRoomById(Long id) {
        var room = hotelRoomDAO.findById(id);
        return room.orElse(null);
    }
}
