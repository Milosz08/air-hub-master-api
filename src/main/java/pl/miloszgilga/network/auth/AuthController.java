/*
 * Copyright (c) 2023 by MILOSZ GILGA <http://miloszgilga.pl>
 *
 * File name: AuthController.java
 * Last modified: 17/05/2023, 16:04
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

package pl.miloszgilga.network.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import pl.miloszgilga.dto.SimpleMessageResDto;
import pl.miloszgilga.network.auth.resdto.LoginResDto;
import pl.miloszgilga.network.auth.reqdto.LoginReqDto;
import pl.miloszgilga.network.auth.reqdto.RegisterReqDto;
import pl.miloszgilga.network.auth.reqdto.ActivateAccountReqDto;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@RestController
@RequestMapping("${api.prefix}/auth")
public class AuthController {

    private final IAuthService authService;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    AuthController(IAuthService authService) {
        this.authService = authService;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @PostMapping("/login")
    ResponseEntity<LoginResDto> login(@RequestBody @Valid LoginReqDto reqDto) {
        return new ResponseEntity<>(authService.login(reqDto), HttpStatus.OK);
    }

    @PostMapping("/register")
    ResponseEntity<SimpleMessageResDto> register(@RequestBody @Valid RegisterReqDto reqDto) {
        return new ResponseEntity<>(authService.register(reqDto), HttpStatus.CREATED);
    }

    @PostMapping("/activate-account")
    ResponseEntity<SimpleMessageResDto> activateAccount(@RequestBody @Valid ActivateAccountReqDto reqDto) {
        return new ResponseEntity<>(authService.activateAccount(reqDto), HttpStatus.OK);
    }

    @PostMapping("/logout")
    ResponseEntity<SimpleMessageResDto> logout() {
        return new ResponseEntity<>(authService.logout(), HttpStatus.OK);
    }
}
