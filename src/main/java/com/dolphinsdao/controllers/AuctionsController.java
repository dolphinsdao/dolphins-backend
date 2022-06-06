package com.dolphinsdao.controllers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.dolphinsdao.dto.NftDto;

import javax.json.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@RestController
@Slf4j public class AuctionsController {
    @Value("${auctions.collection}")
    private String auctionsCollection;
    @Value("${tokens.collection}")
    private String tokensCollection;

    private List<MutableTriple<String, String, String>> auctions = new ArrayList<>();
    private List<NftDto> tokens = new ArrayList<>();
    private List<MutableTriple<String, Double, Double>> coins = new ArrayList<>();
    private JsonObject treasuryState;
    private JsonArray auctionsHistory;

    public boolean tokenExists(String token) {
        return this.tokens.stream().anyMatch(tr -> tr.getAddress().equalsIgnoreCase(token));
    }

    public boolean token(NftDto token) {
        if (this.tokens.stream().anyMatch(existing ->
                existing.getAddress().equalsIgnoreCase(token.getAddress())))
            return false;
        this.tokens.add(token);
        return true;
    }

    public void removeToken(String address) {
        Iterator<NftDto> iterator = this.tokens.iterator();
        while (iterator.hasNext()) {
            NftDto next = iterator.next();
            if (next.getAddress().equalsIgnoreCase(address)) {
                iterator.remove();
                return;
            }
        }
    }

    public void setAuctionsHistory(JsonArray auctionsHistory) {
        this.auctionsHistory = auctionsHistory;
    }

    @GetMapping("/api/v1/auctions")
    public String auctions() {
        return toJsonTokens("auctions", auctions);
    }

    @GetMapping("/api/v1/auctions-history")
    public String auctionsHistory() {
        return auctionsHistory.toString();
    }

    @GetMapping("/api/v1/tokens")
    public String tokens() {
        return toJsonNfts("tokens", tokens);
    }

    @GetMapping("/api/v1/coins")
    public String coins() {
        return toJsonCoins("coins", coins);
    }

    public void setTreasuryState(List<String> titlesList, List<Double> valuesList) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        JsonArrayBuilder titles = Json.createArrayBuilder();
        JsonArrayBuilder value = Json.createArrayBuilder();
        titlesList.forEach(titles::add);
        valuesList.forEach(value::add);

        this.treasuryState = builder.add("titles", titles.build())
                .add("value", value.build())
                .build();
    }

    @GetMapping("/api/v1/treasury-graph")
    public String treasuryGraph() {
        return this.treasuryState.toString();
    }

    public List<NftDto> getTokens() {
        return tokens;
    }

    public void setCoin(String title, Double balance, Double usdt) {
        Optional<MutableTriple<String, Double, Double>> first =
                this.coins.stream().filter(t -> t.getLeft().equalsIgnoreCase(title)).findFirst();
        if (first.isPresent()) {
            first.get().setMiddle(balance);
            first.get().setRight(usdt);
        } else
            this.coins.add(new MutableTriple<>(title, balance, usdt));
    }

    private String toJsonTokens(String root, List<MutableTriple<String, String, String>> auctions) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (Triple<String, String, String> auction : auctions)
            arrayBuilder.add(Json.createObjectBuilder()
                    .add("address", auction.getLeft())
                    .add("description", firstNonNull(auction.getMiddle(), ""))
                    .add("image", auction.getRight()));
        return Json.createObjectBuilder()
                .add(root, arrayBuilder)
                .build().toString();
    }

    private String toJsonNfts(String root, List<NftDto> tokens) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (NftDto token : tokens)
            arrayBuilder.add(Json.createObjectBuilder()
                    .add("project", token.getProject())
                    .add("address", token.getAddress())
                    .add("description", firstNonNull(token.getDescription(), ""))
                    .add("image", token.getImage())
                    .add("floor", token.getFloor()));
        return Json.createObjectBuilder()
                .add(root, arrayBuilder)
                .build().toString();
    }

    private String toJsonCoins(String root, List<MutableTriple<String, Double, Double>> auctions) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (Triple<String, Double, Double> auction : auctions)
            arrayBuilder.add(Json.createObjectBuilder()
                    .add("title", auction.getLeft())
                    .add("balance", auction.getMiddle())
                    .add("sol", auction.getRight()));
        return Json.createObjectBuilder()
                .add(root, arrayBuilder)
                .build().toString();
    }

    @RequestMapping("/images/auctions/{name}")
    @ResponseBody public HttpEntity<byte[]> getAuctionImage(@PathVariable String name) throws IOException {
        return getImage(name, auctionsCollection);
    }

    @RequestMapping("/images/tokens/{name}")
    @ResponseBody public HttpEntity<byte[]> getTokenImage(@PathVariable String name) throws IOException {
        return getImage(name, tokensCollection);
    }

    private HttpEntity<byte[]> getImage(@PathVariable String name, String folder) throws IOException {
        byte[] image = Files.readAllBytes(Path.of(folder, name));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(image.length);

        return new HttpEntity<>(image, headers);
    }
}
