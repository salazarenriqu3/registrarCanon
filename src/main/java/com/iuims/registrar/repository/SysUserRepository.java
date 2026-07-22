package com.iuims.registrar.repository;
import com.iuims.registrar.entity.SysUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Integer> {
    Optional<SysUser> findByUsername(String username);
    List<SysUser> findByRoleAndIsActiveAndAdmissionStatusIn(String role, Boolean isActive, List<String> admissionStatuses);

    @Query("SELECT u FROM SysUser u WHERE LOWER(u.realName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<SysUser> searchUsers(@Param("query") String query);
}
