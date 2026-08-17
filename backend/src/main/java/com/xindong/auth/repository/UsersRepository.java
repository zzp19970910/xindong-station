package com.xindong.auth.repository;

import com.xindong.auth.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByPhone(String phone);

    boolean existsByPhone(String phone);

    @Query("SELECT COUNT(u) > 0 FROM Users u WHERE u.coupleId = :coupleId AND u.id <> :excludeUserId")
    boolean hasOtherPartnerInCouple(@Param("coupleId") Long coupleId, @Param("excludeUserId") Long excludeUserId);

    @Query("SELECT u FROM Users u WHERE u.coupleId = :coupleId AND u.id <> :excludeUserId")
    List<Users> findOtherPartnersInCouple(@Param("coupleId") Long coupleId, @Param("excludeUserId") Long excludeUserId);

    List<Users> findByCoupleId(Long coupleId);
}