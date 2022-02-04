import io.restassured.RestAssured;
import io.restassured.response.Response;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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





    public static void main(String[] args) throws IOException {

        //Define the List that are used to store links
        List<String> listOfMoviesMatchesSearchLinks = new ArrayList<String>();
        List<String> listOfArabicTranslationLinks = new ArrayList<String>();
        List<String> downloadLinks = new ArrayList<String>();

        Response response;

    String movieName = "Ghostbusters.Afterlife.2021.1080p.WEBRip.x264-RARBG";

        listOfMoviesMatchesSearchLinks = searchForMovieAndReturnListOfAvailableCandidates(listOfMoviesMatchesSearchLinks,movieName);

        //Do a fuzzy search to find the best candidate movie
        int movieIndexInList = FuzzySearch.extractOne(movieName, listOfMoviesMatchesSearchLinks).getIndex();


        listOfArabicTranslationLinks = returnListOfArabicTranslations(listOfArabicTranslationLinks, "http://www.subscene.com/subtitles/" + listOfMoviesMatchesSearchLinks.get(movieIndexInList), secondPagePattern);


        for (int i = 0 ;i<listOfArabicTranslationLinks.size();i++) {

            //visit final page to get download link
           downloadLinks = returnTheDownloadLink( downloadPagePattern,listOfMoviesMatchesSearchLinks.get(movieIndexInList), listOfArabicTranslationLinks.get(i));

            downloadTheFile(downloadLinks);

            unZipTheTranslationFile(downloadLinks, "src/main/resources/", "src/main/resources/");
        }
    }

    private static void downloadTheFile(List<String> downloadLinks) throws IOException {
        //Download File
        String downLoadLinkFinal="https://subscene.com/subtitles/arabic-text/"+ downloadLinks.get(0);
        byte[] zipFile  = RestAssured.given()
                .get(downLoadLinkFinal)
                .andReturn().asByteArray();

        //Build the file


        File outputImageFile = new File("src/main/resources",
                downloadLinks.get(0)+".zip"  );

        OutputStream outStream = new FileOutputStream(outputImageFile);
        outStream.write(zipFile);
        outStream.close();
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
        // System.out.println(response.asString());
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

    private static void unZipTheTranslationFile(List<String> downloadLinks, String source,String destination ) {
        //extract the file

        String finalZipFileURI = source+ downloadLinks.get(0)+".zip";
        try {
            ZipFile finalZipFile = new ZipFile(finalZipFileURI);
            File f= new File(finalZipFileURI);
            finalZipFile.extractAll(destination);
            f.delete();

        } catch (ZipException e) {
            e.printStackTrace();
        }
    }


}


