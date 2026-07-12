package rag.example.rag_implementation.services;

import org.springframework.stereotype.Service;
import rag.example.rag_implementation.model.User;
import rag.example.rag_implementation.repository.UserRepository;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getUsers() {
        return userRepository.getAllUsers();
    }

}