import groovy.util.logging.Slf4j;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
public class Scrabber {

    //Define the search Patterns for each page
    private static final Pattern firstPagePattern = Pattern.compile(
            "/subtitles/+(.+?)"+"\">",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    private static final Pattern secondPagePattern = Pattern.compile(
            "/arabic/+(.+?)"+"\">",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    private static final Pattern downloadPagePattern = Pattern.compile(
            "/subtitles/arabic-text/+(.+?)"+"\"",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Scrabber.class);


    public static void main(String[] args) throws IOException {

        //Define the List that are used to store links
        List<String> listOfMoviesMatchesSearchLinks = new ArrayList<String>();
        List<String> listOfArabicTranslationLinks = new ArrayList<String>();
        List<String> downloadLinks ;

        String movieName = args[0];
        String subsFolder = args[1];


      searchForMovieAndReturnListOfAvailableCandidates(listOfMoviesMatchesSearchLinks,movieName);

        //Do a fuzzy search to find the best candidate movie
        int movieIndexInList = FuzzySearch.extractOne(movieName, listOfMoviesMatchesSearchLinks).getIndex();


        returnListOfArabicTranslations(listOfArabicTranslationLinks, "http://www.subscene.com/subtitles/" + listOfMoviesMatchesSearchLinks.get(movieIndexInList), secondPagePattern);


        for (int i = 0 ;i<listOfArabicTranslationLinks.size();i++) {

            //visit final page to get download link
           downloadLinks = returnTheDownloadLink( downloadPagePattern,listOfMoviesMatchesSearchLinks.get(movieIndexInList), listOfArabicTranslationLinks.get(i));

            downloadTheFile(downloadLinks,subsFolder);

            unZipTheTranslationFile(downloadLinks, subsFolder, subsFolder);
        }
    }

    private static void downloadTheFile(List<String> downloadLinks, String subsFolder) throws IOException {
        //Download File
        String downLoadLinkFinal="https://subscene.com/subtitles/arabic-text/"+ downloadLinks.get(0);
        byte[] zipFile  = RestAssured.given()
                .get(downLoadLinkFinal)
                .andReturn().asByteArray();

        //Build the file
        OutputStream outStream = null;


        try {
            File outputImageFile = new File(subsFolder, downloadLinks.get(0)+".zip"  );
             outStream = new FileOutputStream(outputImageFile);

            outStream.write(zipFile);

            outStream.close();
            String filePath = subsFolder + downloadLinks.get(0)+".zip";
            log.info("The following file was written in : {}" ,filePath );
        }catch (Exception e){

            log.error(e.getMessage());
        }
        finally {
            //will enhance this later
        }

    }

    private static List<String> returnListOfArabicTranslations(List<String> listOfArabicTranslationLinks, String s, Pattern secondPagePattern) {
        Response response;

        response = RestAssured.given()
                .get(s).andReturn();

        Matcher secondPageMatcher = secondPagePattern.matcher(response.asString());


        while (secondPageMatcher.find()) {
            listOfArabicTranslationLinks.add(response.asString().substring(secondPageMatcher.start(1),
                    secondPageMatcher.end(1)));
        }
        return listOfArabicTranslationLinks;
    }

    private static List<String> returnTheDownloadLink(Pattern downloadPagePattern,String movie, String translation) {
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
                .queryParam("query",movieName)
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

    private static void unZipTheTranslationFile(List<String> downloadLinks, String source,String destination ) throws NoSuchFileException, DirectoryNotEmptyException, IOException{
        //extract the file

        String finalZipFileURI = source+ downloadLinks.get(0)+".zip";
        Path path = Paths.get(finalZipFileURI);

        try {
            ZipFile finalZipFile = new ZipFile(finalZipFileURI);
            finalZipFile.extractAll(destination);


            Files.delete(path);

        } catch (ZipException e) {
            e.printStackTrace();
        }
        finally {
            log.info("All Zip files are extracted to Subtitle files and deleted" );
        }
    }


}


