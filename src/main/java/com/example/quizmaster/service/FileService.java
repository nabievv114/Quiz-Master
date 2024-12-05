package com.example.quizmaster.service;

import com.example.quizmaster.entity.File;
import com.example.quizmaster.payload.ApiResponse;
import com.example.quizmaster.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    @Value("${file.upload-dir1}")
    private String uploadDir;

    private final FileRepository fileRepository;

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private static final Path root = Paths.get("");

    // Save file
    public ApiResponse saveFile(MultipartFile file) {
        try {
            // Checking file type and determining directory
            String director = checkingAttachmentType(file);
            if (director == null) {
                return new ApiResponse("File yuklash uchun papka topilmadi", HttpStatus.NOT_FOUND);
            }

            Path directoryPath = root.resolve(director);
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
            }

            // Generating unique file name
            String uniqueFileName = generateUniqueFileName(file.getOriginalFilename());
            Path filePath = directoryPath.resolve(uniqueFileName);

            // Saving file to the directory
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Saving file details to the database
            File videoFile = new File();
            videoFile.setFileName(uniqueFileName);
            videoFile.setFilepath(filePath.toString());
            File savedFile = fileRepository.save(videoFile);

            return new ApiResponse("Success", HttpStatus.OK, savedFile.getId());
        } catch (IOException e) {
            logger.error("File save failed: {}", e.getMessage());
            return new ApiResponse("File save failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Check file type and return directory
    public String checkingAttachmentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return null;
        }

        if (contentType.startsWith("image/")) {
            return "images";
        } else if (contentType.startsWith("video/")) {
            return "videos";
        } else if (contentType.startsWith("application/pdf")) {
            return "documents";
        }

        return null;
    }

    // Generate unique file name
    private String generateUniqueFileName(String originalFileName) {
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        return UUID.randomUUID().toString() + extension;
    }

    // Load file as resource
    public Resource loadFileAsResource(Long id) throws IOException {
        File videoFile = fileRepository.findById(id)
                .orElseThrow(() -> new IOException("File not found"));

        Path filePath = Paths.get(videoFile.getFilepath()).normalize();
        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists()) {
            throw new IOException("File not found on server");
        }
        return resource;
    }

    // Update file
    public File updateFile(Long id, MultipartFile file) throws IOException {
        File existingVideoFile = fileRepository.findById(id)
                .orElseThrow(() -> new IOException("File not found"));

        // Delete old file
        Path oldFilePath = Paths.get(existingVideoFile.getFilepath());
        Files.deleteIfExists(oldFilePath);

        // Determine upload path based on file type
        String director = checkingAttachmentType(file);
        if (director == null) {
            throw new IOException("Invalid file type");
        }

        Path uploadPath = root.resolve(director);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Save new file
        String uniqueFileName = generateUniqueFileName(file.getOriginalFilename());
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Update file details in the database
        existingVideoFile.setFileName(uniqueFileName);
        existingVideoFile.setFilepath(filePath.toString());

        return fileRepository.save(existingVideoFile);
    }

    // Delete file
    public ApiResponse deleteFile(Long id) throws IOException {
        File existingVideoFile = fileRepository.findById(id)
                .orElseThrow(() -> new IOException("File not found"));

        // Delete file from server
        Path filePath = Paths.get(existingVideoFile.getFilepath());
        Files.deleteIfExists(filePath);

        // Remove file record from the database
        fileRepository.delete(existingVideoFile);

        return new ApiResponse("Successfully deleted", HttpStatus.OK);
    }
}