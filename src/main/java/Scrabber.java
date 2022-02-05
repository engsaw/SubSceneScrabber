import groovy.util.logging.Slf4j;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static helpers.Helpers.findFiles;
import static java.lang.Thread.sleep;


@Slf4j
public class Scrabber {

    //Define the search Patterns for each page
    private static final Pattern firstPagePattern = Pattern.compile(
            "/subtitles/+(.+?)" + "\">",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    private static final Pattern secondPagePattern = Pattern.compile(
            "/arabic/+(.+?)" + "\">",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    private static final Pattern downloadPagePattern = Pattern.compile(
            "/subtitles/arabic-text/+(.+?)" + "\"",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Scrabber.class);



    public static void main(String[] args) throws IOException, InterruptedException {

        String movieName;
        String subsFolder;

        List<String> allMediaLibList = new ArrayList<String>();

        List<String> files = findFiles(Paths.get(args[0]));
        files.forEach(x -> allMediaLibList.add(x));


        //Define the List that are used to store links
        List<String> listOfMoviesMatchesSearchLinks = new ArrayList<String>();
        List<String> listOfArabicTranslationLinks = new ArrayList<String>();
        List<String> downloadLinks;



        for (String movieNames : allMediaLibList) {


            File f = new File(movieNames);
            movieName = f.getName();
            subsFolder = f.getParent();

            searchForMovieAndReturnListOfAvailableCandidates(listOfMoviesMatchesSearchLinks, movieName);

            //Do a fuzzy search to find the best candidate movie
            int movieIndexInList = FuzzySearch.extractOne(movieName, listOfMoviesMatchesSearchLinks).getIndex();


            listOfArabicTranslationLinks = returnListOfArabicTranslations( "http://www.subscene.com/subtitles/" + listOfMoviesMatchesSearchLinks.get(movieIndexInList), secondPagePattern);


            for (int i = 0; i < listOfArabicTranslationLinks.size(); i++) {

                //visit final page to get download link
                downloadLinks = returnTheDownloadLink(downloadPagePattern, listOfMoviesMatchesSearchLinks.get(movieIndexInList), listOfArabicTranslationLinks.get(i));

                downloadTheFile(downloadLinks, subsFolder);

                unZipTheTranslationFile(downloadLinks, subsFolder, subsFolder);
                sleep (300);
            }
        }
    }

    private static void downloadTheFile(List<String> downloadLinks, String subsFolder) throws IOException {
        //Download File
        String downLoadLinkFinal = "https://subscene.com/subtitles/arabic-text/" + downloadLinks.get(0);
        byte[] zipFile = RestAssured.given()
                .get(downLoadLinkFinal)
                .andReturn().asByteArray();

        //Build the file
        OutputStream outStream = null;


        try {
            File outputImageFile = new File(subsFolder, downloadLinks.get(0) + ".zip");
            outStream = new FileOutputStream(outputImageFile);

            outStream.write(zipFile);

            outStream.close();
            String filePath = subsFolder + downloadLinks.get(0) + ".zip";
            //  log.info("The following file was written in : {}" ,filePath );
        } catch (Exception e) {

            log.error(e.getMessage());
        } finally {
            //will enhance this later
        }

    }

    private static List<String> returnListOfArabicTranslations( String s, Pattern secondPagePattern) {
        Response myResponse;
        List <String> listOfArabicTranslationLinks = new ArrayList<>();

        myResponse = RestAssured.given()
                .get(s).andReturn();

        Matcher secondPageMatcher = secondPagePattern.matcher(myResponse.asString());


        while (secondPageMatcher.find()) {
            listOfArabicTranslationLinks.add(myResponse.asString().substring(secondPageMatcher.start(1),
                    secondPageMatcher.end(1)));
        }
        return listOfArabicTranslationLinks;
    }

    private static List<String> returnTheDownloadLink(Pattern downloadPagePattern, String movie, String translation) {
        Response response;

        String finalLink = "https://subscene.com/subtitles/" + movie + "/arabic/" + translation;
        List<String> downloadLinks = new ArrayList<String>();


        response = RestAssured.given()
                .get(finalLink).andReturn();

        Matcher downloadPageMatcher = downloadPagePattern.matcher(response.asString());


        while (downloadPageMatcher.find()) {
            downloadLinks.add(response.asString().substring(downloadPageMatcher.start(1),
                    downloadPageMatcher.end(1)));
        }
        return downloadLinks;
    }


    private static List<String> searchForMovieAndReturnListOfAvailableCandidates(List<String> listOfMoviesMatchesSearchLinks, String movieName) {
        Response response;
        response = RestAssured.given()
                .queryParam("query", movieName)
                .post("https://subscene.com/subtitles/searchbytitle")
                .andReturn();
        Matcher firstPageMatcher = firstPagePattern.matcher(response.asString());

        while (firstPageMatcher.find()) {
            listOfMoviesMatchesSearchLinks.add(response.asString().substring(firstPageMatcher.start(1),
                    firstPageMatcher.end(1)));
        }

        listOfMoviesMatchesSearchLinks.remove(0);
        listOfMoviesMatchesSearchLinks.remove(0);
        listOfMoviesMatchesSearchLinks.remove(0);

        return listOfMoviesMatchesSearchLinks;
    }

    private static void unZipTheTranslationFile(List<String> downloadLinks, String source, String destination) throws IOException {
        //extract the file

        String finalZipFileURI = source + "/" + downloadLinks.get(0) + ".zip";
        Path path = Paths.get(finalZipFileURI);

        try {
            ZipFile finalZipFile = new ZipFile(finalZipFileURI);
            finalZipFile.extractAll(destination);


            Files.delete(path);

        } catch (ZipException e) {
            e.printStackTrace();
        } finally {
            log.info(finalZipFileURI);
        }
    }


}


