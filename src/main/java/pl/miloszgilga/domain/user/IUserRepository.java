/*
 * Copyright (c) 2023 by MILOSZ GILGA <http://miloszgilga.pl>
 *
 * File name: IUserRepository.java
 * Last modified: 17/05/2023, 15:20
 * Project name: air-hub-master-server
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 *     <http://www.apache.org/license/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the license.
 */

package pl.miloszgilga.domain.user;

import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.ZonedDateTime;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@Repository
public interface IUserRepository extends JpaRepository<UserEntity, Long> {

    @Query(value = "from UserEntity e where e.login = :loginOrEmail or e.emailAddress = :loginOrEmail")
    Optional<UserEntity> findUserByLoginOrEmail(@Param("loginOrEmail") String loginOrEmail);

    @Query(value = "select count(e.id) > 0 from UserEntity e where e.login = :loginOrEmail or e.emailAddress = :loginOrEmail")
    boolean checkIfUserAlreadyExist(@Param("loginOrEmail") String loginOrEmail);

    @Query(value = "select count(e.id) > 0 from UserEntity e where e.login = :login and e.id <> :id")
    boolean checkIfUserWithSameLoginExist(@Param("login") String login, @Param("id") Long id);

    @Query(value = "select count(e.id) > 0 from UserEntity e where e.emailAddress = :emailAddress and e.id <> :id")
    boolean checkIfUserWithSameEmailExist(@Param("emailAddress") String emailAddress, @Param("id") Long id);

    @Modifying
    @Query(value = "delete UserEntity e where e.isActivated = false and e.createdAt < :futureExpierd")
    void deleteAllNotActivatedAccount(@Param("futureExpierd") ZonedDateTime futureExpired);

    @Modifying
    @Query(value = "update UserEntity e set e.isWorkersBlocked = false, e.isRoutesBlocked = false")
    void revalidateAllBlockedWorkersAndRoutes();
}
