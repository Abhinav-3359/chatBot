package rag.example.rag_implementation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import rag.example.rag_implementation.common.ApiResponse;
import rag.example.rag_implementation.dto.LoginRequestDTO;
import rag.example.rag_implementation.dto.LoginResponseDTO;
import rag.example.rag_implementation.dto.RegisterRequestDTO;
import rag.example.rag_implementation.model.User;
import rag.example.rag_implementation.services.AuthService;
import rag.example.rag_implementation.services.UserService;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService,
                          AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getUsers() {

        List<User> users = userService.getUsers();

        return ResponseEntity.ok(
                ApiResponse.success("Users retrieved successfully", users)
        );
    }

@PostMapping("/register")
public ResponseEntity<ApiResponse<Void>> register(
        @RequestBody RegisterRequestDTO request) {

    authService.register(request);

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(
                    ApiResponse.success(
                            "User registered successfully",
                            null
                    )
            );
}

@PostMapping("/login")
public ResponseEntity<ApiResponse<LoginResponseDTO>> login(
        @RequestBody LoginRequestDTO request) {

    return ResponseEntity.ok(
            ApiResponse.success(
                    "Login successful",
                    authService.login(request)
            )
    );
    }
}


 