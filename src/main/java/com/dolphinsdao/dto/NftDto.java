package com.dolphinsdao.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data @Accessors(chain = true)
public class NftDto {
    private String project;
    private String address;
    private String description;
    private String image;
    private Double floor;
}
