package ru.andver00.Service;

import lombok.extern.log4j.Log4j;
import org.springframework.stereotype.Service;
import ru.andver00.Dao.AppUserDAO;
import ru.andver00.Entity.AppUser;

@Log4j
@Service
public class AppUserService implements IAppUserService {
    private final AppUserDAO appUserDAO;

    AppUserService(AppUserDAO appUserDAO) {
        this.appUserDAO = appUserDAO;
    }

    public String registerUser(AppUser appUser) {
        if (appUser.getRegistered()) {
            return "You're already registered";
        }
        else {
            appUserDAO.save(appUser);
            return "Your info has been added to the DB";
        }
    }
}
