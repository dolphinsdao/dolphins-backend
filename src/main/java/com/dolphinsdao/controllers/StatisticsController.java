package com.dolphinsdao.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.json.Json;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@RestController
@RequestMapping("/api/v1")
@Slf4j public class StatisticsController {
    // S1 - Auction proceeds
    private Double auctionsProceeds;
    // D1 - Treasury Value
    private Double treasuryValue;
    // F1 - Profit
    private Double profit;

    public void setAuctionsProceeds(Double auctionsProceeds) {
        this.auctionsProceeds = auctionsProceeds;
    }

    public void setTreasuryValue(Double treasuryValue) {
        this.treasuryValue = treasuryValue;
    }

    public void setProfit(Double profit) {
        this.profit = profit;
    }

    @GetMapping("/balance")
    public ResponseEntity<String> balance() {
        return ResponseEntity.ok(Json.createObjectBuilder()
                .add("auctionsProceeds", firstNonNull(auctionsProceeds, -1D))
                .add("treasuryValue", firstNonNull(treasuryValue, -1D))
                .add("profit", firstNonNull(profit, -1D))
                .build().toString());
    }
}
