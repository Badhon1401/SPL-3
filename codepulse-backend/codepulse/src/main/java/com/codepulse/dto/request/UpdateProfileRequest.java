package com.codepulse.dto.request;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private String avatarUrl;
    private String codeforcesHandle;
    private String leetcodeHandle;
    private String atcoderHandle;
    private String codechefHandle;
}