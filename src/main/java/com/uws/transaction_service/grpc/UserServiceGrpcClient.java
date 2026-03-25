package com.uws.transaction_service.grpc;

import com.uws.user.grpc.proto.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserServiceGrpcClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    /**
     * Get user by UPI ID
     * Used for finding receiver in P2P transfers
     *
     * @param upiId UPI ID format: username@wallet
     * @return UserResponse with userId, kycVerified, active status
     * @throws RuntimeException if user not found or gRPC call fails
     */
    public UserResponse getUserByUpiId(String upiId){
        log.info("gRPC: Calling getUserByUpiId - upiId={}", upiId);

        try {
            GetUserByUpiIdRequest request = GetUserByUpiIdRequest.newBuilder()
                    .setUpiId(upiId)
                    .build();

            UserResponse response = userServiceStub.getUserByUpiId(request);

            if (!response.getSuccess()) {
                log.error("gRPC: User not found - upiId={}, message={}", upiId, response.getMessage());
                throw new RuntimeException("User not found: " + upiId);
            }

            log.info("gRPC: User found - userId={}, upiId={}, kycVerified={}, active={}",
                    response.getUserId(), response.getUpiId(),
                    response.getKycVerified(), response.getActive());

            return response;

        } catch (StatusRuntimeException e) {
            log.error("gRPC: Failed to get user by UPI ID - upiId={}, status={}",
                    upiId, e.getStatus(), e);

            switch (e.getStatus().getCode()) {
                case NOT_FOUND:
                    throw new RuntimeException("User not found: " + upiId, e);
                case UNAVAILABLE:
                    throw new RuntimeException("User service unavailable", e);
                case DEADLINE_EXCEEDED:
                    throw new RuntimeException("User service timeout", e);
                default:
                    throw new RuntimeException("Failed to validate user: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Validate user exists and is active
     * Used for validating sender before initiating transfer
     *
             * @param userId User ID to validate
     * @return ValidationResponse with valid, active, kycVerified flags
     * @throws RuntimeException if validation fails or gRPC call fails
     */
    public ValidationResponse validateUser(String userId) {
        log.info("gRPC: Calling validateUser - userId={}", userId);

        try {
            ValidateUserRequest request = ValidateUserRequest.newBuilder()
                    .setUserId(userId)
                    .build();

            ValidationResponse response = userServiceStub.validateUser(request);

            if (!response.getValid()) {
                log.error("gRPC: User validation failed - userId={}, message={}",
                        userId, response.getMessage());
                throw new RuntimeException("User validation failed: " + response.getMessage());
            }

            log.info("gRPC: User validated - userId={}, active={}, kycVerified={}",
                    userId, response.getActive(), response.getKycVerified());

            return response;

        } catch (StatusRuntimeException e) {
            log.error("gRPC: Failed to validate user - userId={}, status={}",
                    userId, e.getStatus(), e);

            switch (e.getStatus().getCode()) {
                case NOT_FOUND:
                    throw new RuntimeException("User not found: " + userId, e);
                case UNAVAILABLE:
                    throw new RuntimeException("User service unavailable", e);
                case DEADLINE_EXCEEDED:
                    throw new RuntimeException("User service timeout", e);
                default:
                    throw new RuntimeException("Failed to validate user: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Get user profile by userId
     * Used for getting additional user details if needed
     *
     * @param userId User ID
     * @return UserProfileResponse with full profile details
     * @throws RuntimeException if user not found or gRPC call fails
     */
    public UserProfileResponse getUserProfile(String userId) {
        log.info("gRPC: Calling getUserProfile - userId={}", userId);

        try {
            GetUserProfileRequest request = GetUserProfileRequest.newBuilder()
                    .setUserId(userId)
                    .build();

            UserProfileResponse response = userServiceStub.getUserProfile(request);

            if (!response.getSuccess()) {
                log.error("gRPC: Profile not found - userId={}, message={}",
                        userId, response.getMessage());
                throw new RuntimeException("Profile not found: " + userId);
            }

            log.info("gRPC: Profile retrieved - userId={}, upiId={}",
                    userId, response.getUpiId());

            return response;

        } catch (StatusRuntimeException e) {
            log.error("gRPC: Failed to get user profile - userId={}, status={}",
                    userId, e.getStatus(), e);

            switch (e.getStatus().getCode()) {
                case NOT_FOUND:
                    throw new RuntimeException("User profile not found: " + userId, e);
                case UNAVAILABLE:
                    throw new RuntimeException("User service unavailable", e);
                default:
                    throw new RuntimeException("Failed to get profile: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Check if UPI ID exists (quick check without full validation)
     *
     * @param upiId UPI ID to check
     * @return true if UPI ID exists, false otherwise
     */
    public boolean upiIdExists(String upiId) {
        log.info("gRPC: Checking if UPI ID exists - upiId={}", upiId);

        try {
            UserResponse response = getUserByUpiId(upiId);
            return response.getSuccess();
        } catch (Exception e) {
            log.debug("gRPC: UPI ID does not exist - upiId={}", upiId);
            return false;
        }
    }

}
