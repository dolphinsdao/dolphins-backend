package com.dolphinsdao.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.dolphinsdao.controllers.AuctionsController;
import com.dolphinsdao.controllers.StatisticsController;
import com.dolphinsdao.dto.NftDto;
import com.dolphinsdao.tools.ConvertResizer;
import com.dolphinsdao.tools.ImageResizer;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Service
@Slf4j public class GoogleSheetsTreasuryService {
    private AuctionsController auctionsController;
    private StatisticsController statisticsController;

    private static final String APPLICATION_NAME = "DolphinsDAO service";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String KEY_FILE_LOCATION = "/credentials.json";

    private static final String TREASURY_SPREADSHEET_ID = "1ZrefpkvJJ9Z5k83qXbOK8CjFgGSxcnRsj7zyrcQZxNU";

    @Value("${tokens.collection}")
    private String tokensCollection;
    @Value("${auctions.collection}")
    private String auctionsCollection;
    @Value("${image-resizer}")
    private ImageResizerType resizer;
    private Set<String> resizableFormats = new HashSet<>(
            Arrays.asList("png", "jpg", "jpeg")
    );

    public GoogleSheetsTreasuryService(AuctionsController auctionsController,
                                       StatisticsController statisticsController) {
        this.auctionsController = auctionsController;
        this.statisticsController = statisticsController;
    }

    @Scheduled(fixedDelay = 60 * 1000)
    public void sync() throws GeneralSecurityException, IOException {
        new File(tokensCollection).mkdirs();
        new File(auctionsCollection).mkdirs();

        HttpTransport httpTransport = null;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleCredential credential = GoogleCredential
                    .fromStream(GoogleSheetsTreasuryService.class.getResourceAsStream(KEY_FILE_LOCATION))
                    .createScoped(SheetsScopes.all());

            // Construct the Analytics service object.
            Sheets build = new Sheets.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME).build();


            ValueRange execute = build.spreadsheets().values()
                    .get(TREASURY_SPREADSHEET_ID,"Treasury - Tokens!A2:F1000").execute();
            for (List<Object> value : execute.getValues()) {
                if (value.size() > 4) {
                    auctionsController.setCoin(value.get(0).toString(),
                            Double.parseDouble(firstNonNull(value.get(1), "0").toString().replaceAll(",", "")),
                            Double.parseDouble(firstNonNull(value.get(4), "0").toString().replaceAll(",", "")));

                    log.info("{}", value);
                } else
                    log.info("Coin row skipped due to it's size: {}", value);
            }


            // S1 - Auction proceeds
            // D1 - Treasury Value
            // F1 - Profit

            execute = build.spreadsheets().values()
                    .get(TREASURY_SPREADSHEET_ID, "Auctions!L1").execute();
            statisticsController.setAuctionsProceeds(Double.parseDouble(execute.getValues().get(0).get(0)
                    .toString().replaceAll(",", "")));
            execute = build.spreadsheets().values()
                    .get(TREASURY_SPREADSHEET_ID, "Auctions!L12").execute();
            statisticsController.setProfit(Double.parseDouble(execute.getValues().get(0).get(0)
                    .toString().replaceAll(",", "").replaceAll("%", "")));

            List<String> old = this.auctionsController.getTokens().stream()
                    .map(NftDto::getAddress).collect(Collectors.toList());
            // Project name	Quantity	Purchased for	Floor price ($SOL)	Address	Image	Description
            execute = build.spreadsheets().values()
                    .get(TREASURY_SPREADSHEET_ID, "Treasury - NFTs!A2:H1000").execute();
            for (List<Object> value : execute.getValues()) {
                if (value == null || value.isEmpty()) continue;
                if (value.size() < 7) {
                    log.error("Wrong number of columns for token row");
                    continue;
                } else if (value.get(0) == null || value.get(0).toString().isBlank()) {
                    log.error("Missing value for project name at {}", value);
                    continue;
                } else if (value.get(3) == null || value.get(3).toString().isBlank()
                        || !value.get(3).toString().replaceAll(",", ".").matches("\\d+(\\.\\d+){0,1}")) {
                    log.error("Missing value for floor price at {}", value);
                    continue;
                } else if (value.get(4) == null || value.get(4).toString().isBlank()) {
                    log.error("Missing value for address at {}", value);
                    continue;
                } else if (value.get(5) == null || value.get(5).toString().isBlank()) {
                    log.error("Missing value for image at {}", value);
                    continue;
                } else if (value.get(6) == null || value.get(6).toString().isBlank()) {
                    log.error("Missing value for description", value);
                    continue;
                }
                String project = value.get(0).toString();
                String address = value.get(4).toString();
                String image = value.get(5).toString();
                String description = value.get(6).toString();
                Double floor = Double.parseDouble(value.get(3).toString().replaceAll(",", "."));

                if (!auctionsController.tokenExists(address)) {
                    try {
                        String ext = image.lastIndexOf(".") >= image.length() - 4
                                ? image.substring(image.lastIndexOf(".") + 1)
                                : image.matches(".*ext=\\S+")
                                ? image.replaceAll(".*ext=(\\S+)", "$1")
                                : "png";
                        File file = downloadFile(new URL(image), new File(tokensCollection, address + "." + ext));
                        if (resizableFormats.contains(ext.toLowerCase()))
                            switch (resizer) {
                                case convert:
                                    ConvertResizer.resize(file, file, 350, 350);
                                    break;
                                case internal:
                                    ImageResizer.resize(file.getAbsolutePath(), file.getAbsolutePath(), 350, 350);
                                    break;
                            }
                        else
                            log.info("{} has non resizable format: {} -- resize skipped", address, ext);
                        this.auctionsController.token(new NftDto()
                                .setProject(project)
                                .setAddress(address)
                                .setDescription(firstNonNull(value.get(6).toString(), ""))
                                .setImage("/images/tokens/" + file.getName())
                                .setFloor(floor));
                        old.remove(address);
                        log.info("Processed {}", value);
                    } catch (Exception ex) {
                        log.error("Error while processing token at row: {}", value);
                    }
                } else {
                    this.auctionsController.getTokens().stream().filter(
                            t -> t.getAddress().equalsIgnoreCase(address)).findFirst().ifPresent(token -> {
                        token.setProject(project);
                        token.setDescription(description);
                    });
                    log.info("Updated {}", value);
                    old.remove(address);
                }
            }
            // drop removed from table tokens:
            old.forEach(address -> this.auctionsController.removeToken(address));

            // load graph data:
            execute = build.spreadsheets().values()
                    .get(TREASURY_SPREADSHEET_ID, "Treasury - Graph!A2:B1000").execute();

            List<String> titles = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.ENGLISH);
            for (List<Object> value : execute.getValues()) {
                if (value.size() < 2) continue;
                if (value.get(0) == null || value.get(0).toString().isBlank()) continue;
                if (value.get(1) == null || !value.get(1).toString().replaceAll(",", ".").matches("\\d+(\\.\\d+){0,1}")) continue;
                if (value.get(0).toString().matches("\\d+-\\d+-\\d+")) {
                    titles.add(dateFormat.format(
                            Date.from(LocalDate.parse(value.get(0).toString()).atStartOfDay()
                                    .atZone(ZoneId.systemDefault()).toInstant())));
                } else
                    titles.add(value.get(0).toString());
                values.add(Double.parseDouble(value.get(1).toString().replaceAll(",", ".")));
            }
            if (!values.isEmpty())
                statisticsController.setTreasuryValue(values.get(values.size() - 1));
            this.auctionsController.setTreasuryState(titles, values);
        } finally {
            if (httpTransport != null)
                try {
                    httpTransport.shutdown();
                } catch (Exception ignored) {}
        }

    }


    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void syncAuctions() throws GeneralSecurityException, IOException {
        HttpTransport httpTransport = null;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleCredential credential = GoogleCredential
                    .fromStream(GoogleSheetsTreasuryService.class.getResourceAsStream(KEY_FILE_LOCATION))
                    .createScoped(SheetsScopes.all());
            // Construct the Analytics service object.
            Sheets build = new Sheets.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME).build();

            ValueRange execute = build.spreadsheets().values()
                    .get(TREASURY_SPREADSHEET_ID, "Auctions!A2:I105").execute();
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            for (List<Object> row : execute.getValues()) {
                try {
                    String image = firstNonNull(row.get(3), "").toString();
                    String state = firstNonNull(row.get(8), "UNKNOWN").toString();
                    int num = Integer.parseInt(row.get(0).toString());
                    JsonObjectBuilder objectBuilder = Json.createObjectBuilder()
                            .add("num", num)
                            .add("address", firstNonNull(row.get(2), "").toString())
                            .add("image", image)
                            .add("description", firstNonNull(row.get(4), "").toString())
                            .add("state", state)
                            .add("date", firstNonNull(row.get(1), "").toString());
                    if (image.isBlank()) {
                        log.info("Auction #{} skipped: image is blank", num);
                        continue;
                    }
                    if (row.size() > 5 && row.get(5) != null && !row.get(5).toString().isBlank())
                        objectBuilder.add("winnerBid",
                                Double.parseDouble(row.get(5).toString().replaceAll(",", ".")));
                    if (row.size() > 6 && row.get(6) != null)
                        objectBuilder.add("winner", row.get(6).toString());
                    if (row.size() > 7 && row.get(7) != null) {
                        objectBuilder.add("link", row.get(7).toString());
                    }
                    if (state.equals("UNKNOWN") || state.equals("UPCOMING") || state.equals("UNSOLD")) {
                        log.info("Auction #{} skipped: ", num);
                        continue;
                    }
                    String ext = "png";
                    File file = new File(auctionsCollection, num + "." + ext);
                    if (!file.exists()) file = downloadFile(new URL(image), file);
                    File to = new File(auctionsCollection, num + "_s." + ext);
                    if (!to.exists()) {
                        if (resizableFormats.contains(ext.toLowerCase()))
                            switch (resizer) {
                                case convert:
                                    ConvertResizer.resize(file, to, 350, 350);
                                    break;
                                case internal:
                                    ImageResizer.resize(file.getAbsolutePath(),
                                            to.getAbsolutePath(),
                                            140, 140);
                                    break;
                            }
                        else {
                            log.info("{} has non resizable format: {} -- resize skipped", num, ext);
                            to = file;
                        }
                    }
                    objectBuilder.add("imageS", "/images/auctions/" + to.getName());
                    arrayBuilder.add(objectBuilder.build());
                    log.info("Processed auction #{}", num);
                } catch (Exception ex) {
                    log.error("Error while processing auction history {}, {}", ex.getMessage(), row);
                }
            }
            auctionsController.setAuctionsHistory(arrayBuilder.build());
        } finally {
            if (httpTransport != null)
                try {
                    httpTransport.shutdown();
                } catch (Exception ignored) {}
        }

    }

    private static final int TIMEOUT = 60 * 1000;

    private static File downloadFile(URL url, File outputFile) throws IOException {
        HttpURLConnection connection = null;
        ReadableByteChannel rbc = null;
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {

            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);

            rbc = Channels.newChannel(connection.getInputStream());
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } finally {
            if (rbc != null)
                try {
                    rbc.close();
                } catch (Exception ignored) {}
            if (connection != null)
                try {
                    connection.disconnect();
                } catch (Exception ignored) {}
        }
        return outputFile;
    }
}
