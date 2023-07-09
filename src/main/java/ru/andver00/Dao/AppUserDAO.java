package ru.andver00.Dao;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andver00.Entity.AppUser;

public interface AppUserDAO extends JpaRepository<AppUser, Long> {
    AppUser findAppUserByTelegramUserId(Long id);
}
