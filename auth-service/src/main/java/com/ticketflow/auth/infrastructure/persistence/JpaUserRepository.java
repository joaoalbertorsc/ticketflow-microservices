package com.ticketflow.auth.infrastructure.persistence;

import com.ticketflow.auth.domain.Role;
import com.ticketflow.auth.domain.User;
import com.ticketflow.auth.domain.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface JpaUserRepository extends JpaRepository<User, UUID>, UserRepository {

	@Override
	@Query("select count(u) > 0 from User u join u.roles r where r = :role")
	boolean existsByRole(@Param("role") Role role);
}
