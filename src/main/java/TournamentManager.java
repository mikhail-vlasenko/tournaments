import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import javafx.util.Pair;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.*;

public class TournamentManager {
    private static String SHEET_ID = "1aQqQ9ldMDDjqU0rG1en9B5_tnnX_cpJr1ZHmi5k29ac";
    private static Sheets sheetsService;
    private static final String APPLICATION_NAME = "Tournament Manager";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/client_id.json";

    private static Credential authorize() throws IOException, GeneralSecurityException {
        // Load client secrets.
        InputStream in = TournamentManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    private static Sheets getSheetsService() throws IOException, GeneralSecurityException {
        Credential credential = authorize();
        return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private static List<List<Object>> read(String range) throws IOException, GeneralSecurityException {
        if (!range.contains("!")){
            System.out.println(range);
            throw new IllegalArgumentException("sheet not specified");
        }
        range = range.toUpperCase();
        sheetsService = getSheetsService();
        ValueRange response = sheetsService.spreadsheets().values().get(SHEET_ID, range).execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()){
            System.out.println("No data found");
        }
        return values;
    }

    private static void write(String range, List<List<Object>> values) throws IOException, GeneralSecurityException {
        if (!range.contains("!")){
            System.out.println(range);
            throw new IllegalArgumentException("sheet not specified");
        }
        range = range.toUpperCase();
        sheetsService = getSheetsService();
        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values().update(SHEET_ID, range, body).setValueInputOption("RAW").execute();
    }

    private static Pair<String, String> waitExecute(String sheet) throws InterruptedException, IOException, GeneralSecurityException {
        if (!sheet.contains("!")){
            sheet = sheet + "!";
        }
        sheetsService = getSheetsService();
        List<List<Object>> values = read(sheet+"e1");
        while (values.get(0).get(0).equals("type action to execute")) {
            Thread.sleep(1000);
            sheetsService = getSheetsService();
            try {
                values = read(sheet + "e1:f1");
            }catch (java.net.SocketTimeoutException e){
                System.err.println("Caught TimeoutException");
            }
        }
        write(sheet+"e1:f1", Arrays.asList(Arrays.asList("type action to execute", "execution parameter")));
        return new Pair<String, String>((String) values.get(0).get(0), (String) values.get(0).get(1));
    }

    private static void initLayout() throws IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
        write("Sheet1!A1:F1", Arrays.asList(
                Arrays.asList("input names below, number here", "sport", "input 1 where applies",
                        "", "type action to execute", "execution parameter")));
        write("Sheet1!B2:B4", Arrays.asList(Arrays.asList("chess"),
                Arrays.asList("table tennis"), Arrays.asList("football")));
    }

    private static List<Object> getNames() throws IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
        List<List<Object>> range = read("Sheet1!a1");
        int numRange = Integer.parseInt((String) range.get(0).get(0));
        List<List<Object>> values = read("Sheet1!a2:a"+Integer.toString(numRange + 1));
        List<Object> names = new ArrayList<> ();
        for (List row : values){
            names.add(row.get(0));
        }
        return names;
    }

    private static void addTab(String title) throws IOException, GeneralSecurityException {
        boolean flag = false;
        sheetsService = getSheetsService();
        Spreadsheet ssheet = sheetsService.spreadsheets().get(SHEET_ID).execute();
        for (Sheet sheet : ssheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(title)){
                System.out.printf("sheet %s already exists\n", title);
                flag = true;
            }
        }
        if (!flag) {
            List<Request> requests = new ArrayList<>();
            AddSheetRequest addSheet = new AddSheetRequest().setProperties(new SheetProperties().setTitle(title));
            Request request = new Request().setAddSheet(addSheet);
            requests.add(request);
            BatchUpdateSpreadsheetRequest requestBody = new BatchUpdateSpreadsheetRequest();
            requestBody.setRequests(requests);
            sheetsService.spreadsheets().batchUpdate(SHEET_ID, requestBody).execute();
        }
    }

    private static void initStructure(List<Object> names) throws IOException, GeneralSecurityException {
        String range;
        sheetsService = getSheetsService();
        //System.out.println(Math.log(names.size()) / Math.log(2));
        List<List<Object>> values = new ArrayList<> ();
        for (int i = 0; i < names.size() * 2; i++) {
            if (i % 2 == 0) {
                values.add(Arrays.asList(names.get(i / 2)));
            }
            else{
                values.add(Arrays.asList(""));
            }
        }
        range = "Tournament structure!a1:a";
        write(range + (2 * names.size()), values);
        write("Tournament structure!E1:F1", Arrays.asList(Arrays.asList("type action to execute", "execution parameter")));
    }

    // lol there are no parameters with default values
    // have to use overloading instead
    private static List<List<Object>> act(String action, String range, List<List<Object>> values)
            throws IOException, GeneralSecurityException, InterruptedException {
        switch (action) {
            case "read":
                values = read(range);
                System.out.println("values READ");
                break;
            case "write":
                write(range, values);
                System.out.println("values WRITTEN");
                break;
            case "init":
                initLayout();
                System.out.println("LAYOUT LOADED");
                break;
            case "make str":
                addTab("Tournament structure");
                initStructure(getNames());
                System.out.println("STRUCTURE MADE");
                break;
            case "make stats":
                addTab("Stats");
                System.out.println("STATS MADE");
                break;
            default:
                System.out.println("incorrect input");
                System.err.println("incorrect input");
        }
        return values;
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String action, range="", strValues="";
        List<Object> names;
        List<List<Object>> receivedValues;
        Pair<String, String> exeParams;
        while (true) {
            System.out.println("enter your action, range and values if any:");
            action = scanner.nextLine();
            if (action.equals("exit")){
                break;
            }
            if (action.equals("get names")){
                names = getNames();
            }
            if (action.equals("read") || action.equals("write")) {
                range = scanner.nextLine().toUpperCase();
                range = "Sheet1!" + range;
            }
            if (action.equals("write")) {
                strValues = scanner.nextLine();
            }

            System.out.println("processing");
            List<List<Object>> values = Arrays.asList(Arrays.asList(strValues));
            if (action.equals("wait")){
                exeParams = waitExecute("Tournament structure!");
                action = exeParams.getKey();
                range = "Tournament structure!" + exeParams.getValue();
            }
            receivedValues = act(action, range, values);
            System.out.println(receivedValues);
        }
    }
}
