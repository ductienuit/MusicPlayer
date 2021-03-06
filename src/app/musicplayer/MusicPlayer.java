package app.musicplayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.LogManager;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import app.musicplayer.model.Album;
import app.musicplayer.model.Artist;
import app.musicplayer.model.Library;
import app.musicplayer.model.Song;
import app.musicplayer.util.Resources;
import app.musicplayer.util.XMLEditor;
import app.musicplayer.view.ImportMusicDialogController;
import app.musicplayer.view.MainController;
import app.musicplayer.view.NowPlayingController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * @author DucTien
 */
public class MusicPlayer extends Application {

    private static MainController mainController;
    private static MediaPlayer mediaPlayer;
    private static ArrayList<Song> nowPlayingList;
    private static int nowPlayingIndex;
    private static Song nowPlaying;
    private static Timer timer;
    private static int timerCounter;
    private static int secondsPlayed;
    private static boolean isLoopActive = false;
    private static boolean isShuffleActive = false;
    private static boolean isMuted = false;
    private static Object draggedItem;

    private static Stage stage;

    // Lưu số file nhạc trong library.xml.
    // Số này được dùng để so sánh với số file trong thư mục nhạc để kiểm tra folder
    // có thêm hay xóa nhạc hay không mục đích là cập nhật library.xml
    private static int xmlFileNum;


    //Lưu ID bài nhạc cuối cùng, chú ý là ID của bài hát trong library.xml
    //mục đích là để kiểm tra số id vì lỡ có thằng nào xóa một file nhạc thì khi cập nhật
    //mình biết id để đặt id cho các bài nhạc tiếp theo.
    private static int lastIdAssigned;

    public static void main(String[] args) {
        Application.launch(MusicPlayer.class);
    }

    @Override
    public void start(Stage stage) throws Exception {

        // Suppresses warning caused by converting music library data into xml file.
        LogManager.getLogManager().reset();
        PrintStream dummyStream = new PrintStream(new OutputStream() {
            public void write(int b) {
                //do nothing
            }
        });
        System.setOut(dummyStream);
        System.setErr(dummyStream);

        timer = new Timer();
        timerCounter = 0;
        secondsPlayed = 0;

        MusicPlayer.stage = stage;
        MusicPlayer.stage.setTitle("Music Player");
        MusicPlayer.stage.getIcons().add(new Image(this.getClass().getResource(Resources.IMG + "Icon.png").toString()));
        MusicPlayer.stage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        try {
            // Load main layout from fxml file.
            FXMLLoader loader = new FXMLLoader(this.getClass().getResource(Resources.FXML + "SplashScreen.fxml"));
            VBox view = loader.load();

            // Shows the scene containing the layout.
            Scene scene = new Scene(view);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();

            //Hàm kiểm tra library.xml có tồn tại, nếu không có thì tạo mới.
            checkLibraryXML();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("Loi: "+ex);
            System.exit(0);
        }

        Thread thread = new Thread(() -> {
            // Lấy song, album, artist, và playlist data từ library xml
            Library.getSongs();
            Library.getAlbums();
            Library.getArtists();
            Library.getPlaylists();

            //Load danh sách bài hát đang được chơi
            nowPlayingList = Library.loadPlayingList();

            //Nếu danh sách rổng, lấy hai bài hát của ca sĩ đầu tiên
            if (nowPlayingList.isEmpty()) {

                Artist artist = Library.getArtists().get(0);

                for (Album album : artist.getAlbums()) {
                    nowPlayingList.addAll(album.getSongs());
                }

                Collections.sort(nowPlayingList, (first, second) -> {
                    Album firstAlbum = Library.getAlbum(first.getAlbum());
                    Album secondAlbum = Library.getAlbum(second.getAlbum());
                    if (firstAlbum.compareTo(secondAlbum) != 0) {
                        return firstAlbum.compareTo(secondAlbum);
                    } else {
                        return first.compareTo(second);
                    }
                });
            }

            //Set các thuộc tính khi khởi động chương trình(Bài hát đang chơi, index playing, ...)
            nowPlaying = nowPlayingList.get(0);
            nowPlayingIndex = 0;
            nowPlaying.setPlaying(true);
            timer = new Timer();
            timerCounter = 0;
            secondsPlayed = 0;
            String path = nowPlaying.getLocation();
            Media media = new Media(Paths.get(path).toUri().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(0.5);
            //Xếp vào queue thread cần xử lí (đây là bài hát tiếp theo)
            mediaPlayer.setOnEndOfMedia(new SongSkipper());

            File imgFolder = new File(Resources.JAR + "/img");
            if (!imgFolder.exists()) {
                // Nếu thư mục img không tồn tại, tạo thread để download Artist Image
                // vào thư mục img tương ứng với artist bài hát
                Thread thread1 = new Thread(() -> {
                    Library.getArtists().forEach(Artist::downloadArtistImage);
                });

                // Nếu thư mục img không tồn tại, tạo thread để download Artwork Image
                // vào thư mục img tương ứng với Artwork bài hát
                Thread thread2 = new Thread(() -> {
                    Library.getAlbums().forEach(Album::downloadArtwork);
                });

                thread1.start();
                thread2.start();
            }

            //Download Artist image với những bài hát mới
            new Thread(() -> {
                XMLEditor.getNewSongs().forEach(song -> {
                    try {
                        Library.getArtist(song.getArtist()).downloadArtistImage();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }).start();

            // Gọi hàm khởi hàm main layout
            Platform.runLater(this::initMain);
        });

        thread.start();
    }

    private static void checkLibraryXML() {
        // Tìm JAR file và đường dẫn của folder hiện tại
        File musicPlayerJAR = null;
        try {
            musicPlayerJAR = new File(MusicPlayer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        String jarFilePath = musicPlayerJAR.getParentFile().getPath();

        // Lưu đường dẫn folder vào đường dẫn filepath của xml, trong class thì lưu trong Resources.java
        Resources.JAR = jarFilePath + "/";

        // Đường dẫn trực tiếp của file library.xml và vị trí lưu nó.
        File libraryXML = new File(Resources.JAR + "library.xml");

        // Nếu file tồn tại, kiểm tra xem đường dẫn folder nhạc có thay đổi
        Path musicDirectory;
        if (libraryXML.exists()) {
            // Lấy đường dẫn folder âm nhạc từ xml file sau đó đếm số file trong folder
            // so sánh với số numFile trong xml file, để xem xét việc cập nhật library.xml
            musicDirectory = xmlMusicDirPathFinder();

            // Try/catch trường hợp folder bị thay đổi
            try {
                // Lấy số bài hát thực trong folder nhạc và Lấy số bài hát trong file xml
                // So sánh để biết cần cập nhật hay không
                int musicDirFileNum = musicDirFileNumFinder(musicDirectory.toFile(), 0);
                xmlFileNum = xmlMusicDirFileNumFinder();

                //Nếu khác nhau, thư viện nhạc bị thay đổi. Phải cập nhật library.xml
                if (musicDirFileNum != xmlFileNum) {
                    // Cập nhật file library.xml từ folder nhạc
                    updateLibraryXML(musicDirectory);
                }
                // NullPointerException thrown by musicDirFileNumFinder().
                // Nó gặp lỗi vì đường dẫn có thể không còn tồn tại thay đổi
            } catch (NullPointerException npe) {
                createLibraryXML();
                //Lấy số file được lưu trong xml
                xmlFileNum = xmlMusicDirFileNumFinder();
                // Gets music directory path from xml file so that it can be passed as an argument when creating the directory watch.
                musicDirectory = xmlMusicDirPathFinder();
            }

            // If the library.xml file does not exist, the file is created from the user specified music library location.
        } else if (!libraryXML.exists()) {
            createLibraryXML();
            // Gets the number of files saved in the xml file.
            xmlFileNum = xmlMusicDirFileNumFinder();
            // Gets music directory path from xml file so that it can be passed as an argument when creating the directory watch.
            musicDirectory = xmlMusicDirPathFinder();        
        }
        
        while(xmlFileNum==0)
            NoFilesInMusicDir();
    }

    /**
     * Thông báo đường dẫn không có file âm nhạc
     */
    public static void NoFilesInMusicDir() {
            //Show Alert thông báo không có file nhạc trong folder
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText("Đường dẫn trống");
            alert.setContentText("Đường dẫn của bạn không có file nhạc!");
            alert.showAndWait();

            createLibraryXML();
    }

    /**
     *  Lấy đường dẫn vào folder nhạc của library.xml
      * @return Path đường dẫn folder music
     */
    private static Path xmlMusicDirPathFinder() {
        try {
            // Creates reader for xml file.
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty("javax.xml.stream.isCoalescing", true);
            FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
            XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

            String element = null;
            String path = null;

            // Loops through xml file looking for the music directory file path.
            while(reader.hasNext()) {
                reader.next();
                if (reader.isWhiteSpace()) {
                    continue;
                } else if (reader.isStartElement()) {
                    element = reader.getName().getLocalPart();
                } else if (reader.isCharacters() && element.equals("path")) {
                    path = reader.getText();
                    break;
                }
            }
            // Closes xml reader.
            reader.close();

            return Paths.get(path);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     *  Số bài hát trong được lưu trong file xml
     */
    private static int xmlMusicDirFileNumFinder() {
        try {
            // Creates reader for xml file.
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty("javax.xml.stream.isCoalescing", true);
            FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
            XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

            String element = null;
            String fileNum = null;

            // Loops through xml file looking for the music directory file path.
            while(reader.hasNext()) {
                reader.next();
                if (reader.isWhiteSpace()) {
                    continue;
                } else if (reader.isStartElement()) {
                    element = reader.getName().getLocalPart();
                } else if (reader.isCharacters() && element.equals("fileNum")) {
                    fileNum = reader.getText();
                    break;
                }
            }
            // Closes xml reader.
            reader.close();

            // Converts the file number to an int and returns the value.
            return Integer.parseInt(fileNum);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Số file trong folder nhạc
     * @param musicDirectory Đường dẫn folder nhạc
     * @param i giá trị được trả về
     * @return
     */
    private static int musicDirFileNumFinder(File musicDirectory, int i) {
        // Lists all the files in the music directory and stores them in an array.
        File[] files = musicDirectory.listFiles();

        // Loops through the files, increments counter if file is found.
        for (File file : files) {
            if (file.isFile() && Library.isSupportedFileType(file.getName())) {
                i++;
            } else if (file.isDirectory()) {
                i = musicDirFileNumFinder(file, i);
            }
        }
        return i;
    }

    /**
     * Cập nhật library.xml nếu như folder nhạc có thêm hoặc xóa, sửa một bài hát nào đó
     * @param musicDirectory
     */
    private static void updateLibraryXML(Path musicDirectory) {
        // Cài đặt đường dẫn folder nhạc vào XMLEditor.
        XMLEditor.setMusicDirectory(musicDirectory);

        // Kiểm tra nếu các bài hát cần được thêm hoặc cần được xóa trên xml file
        XMLEditor.addDeleteChecker();
    }

    /**
     * Hiển thị dialog hỗ trợ tạo file library.xml
     * Click vào Import button sẽ chạy task tạo library.xml
     */
    private static void createLibraryXML() {
        try {
            FXMLLoader loader = new FXMLLoader(MusicPlayer.class.getResource(Resources.FXML + "ImportMusicDialog.fxml"));
            BorderPane importView = loader.load();

            // Create the dialog Stage.
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Music Player Configuration");
            // Forces user to focus on dialog.
            dialogStage.initModality(Modality.WINDOW_MODAL);
            // Sets minimal decorations for dialog.
            dialogStage.initStyle(StageStyle.UTILITY);
            // Prevents the alert from being re-sizable.
            dialogStage.setResizable(false);
            dialogStage.initOwner(stage);

            // Sets the import music dialog scene in the stage.
            dialogStage.setScene(new Scene(importView));

            // Set the dialog into the controller.
            ImportMusicDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);

            // Show the dialog and wait until the user closes it.
            dialogStage.showAndWait();

            // Checks if the music was imported successfully. Closes the application otherwise.
            boolean musicImported = controller.isMusicImported();
            if (!musicImported) {
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initializes the main layout.
     */
    private void initMain() {
        try {
            // Load main layout from fxml file.
            FXMLLoader loader = new FXMLLoader(this.getClass().getResource(Resources.FXML + "Main.fxml"));
            BorderPane view = loader.load();

            // Shows the scene containing the layout.
            double width = stage.getScene().getWidth();
            double height = stage.getScene().getHeight();

            view.setPrefWidth(width);
            view.setPrefHeight(height);

            Scene scene = new Scene(view);
            stage.setScene(scene);

            // Gives the controller access to the music player main application.
            mainController = loader.getController();
            mediaPlayer.volumeProperty().bind(mainController.getVolumeSlider().valueProperty().divide(200));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static class SongSkipper implements Runnable {
        @Override
        public void run() {
            skip();
        }
    }

    private static class TimeUpdater extends TimerTask {
        private int length = (int) getNowPlaying().getLengthInSeconds() * 4;

        @Override
        public void run() {
            Platform.runLater(() -> {
                if (timerCounter < length) {
                    if (++timerCounter % 4 == 0) {
                        mainController.updateTimeLabels();
                        secondsPlayed++;
                    }
                    if (!mainController.isTimeSliderPressed()) {
                        mainController.updateTimeSlider();
                    }
                }
            });
        }
    }

    /**
     * Plays selected song.
     */
    public static void play() {
        if (mediaPlayer != null && !isPlaying()) {
            mediaPlayer.play();
            timer.scheduleAtFixedRate(new TimeUpdater(), 0, 250);
            mainController.updatePlayPauseIcon(true);
        }
    }

    /**
     * Checks if a song is playing.
     */
    public static boolean isPlaying() {
        return mediaPlayer != null && MediaPlayer.Status.PLAYING.equals(mediaPlayer.getStatus());
    }

    /**
     * Pauses selected song.
     */
    public static void pause() {
        if (isPlaying()) {
            mediaPlayer.pause();
            timer.cancel();
            timer = new Timer();
            mainController.updatePlayPauseIcon(false);
        }
    }

    public static void seek(int seconds) {
        if (mediaPlayer != null) {
            mediaPlayer.seek(new Duration(seconds * 1000));
            timerCounter = seconds * 4;
            mainController.updateTimeLabels();
        }
    }

    /**
     * Skips song.
     */
    public static void skip() {
        //Nếu nằm bài hát hiện tại nằm trong size của playlist thì chơi bài tiếp theo
        if (nowPlayingIndex < nowPlayingList.size() - 1) {
            boolean isPlaying = isPlaying();
            mainController.updatePlayPauseIcon(isPlaying);
            setNowPlaying(nowPlayingList.get(nowPlayingIndex + 1));
            if (isPlaying) {
                play();
            }
        }
        //Nếu index không nằm trong size playlist
        else if (isLoopActive) {
            //Kiểm tra có lặp bài hát đang chơi, nếu có chơi lại
            boolean isPlaying = isPlaying();
            mainController.updatePlayPauseIcon(isPlaying);
            nowPlayingIndex = 0;
            setNowPlaying(nowPlayingList.get(nowPlayingIndex));
            if (isPlaying) {
                play();
            }
        } else {
            //Nếu không lặp thì quay lại bài đầu tiên trong ds
            mainController.updatePlayPauseIcon(false);
            nowPlayingIndex = 0;
            setNowPlaying(nowPlayingList.get(nowPlayingIndex));
        }
    }

    public static void back() {
        if (timerCounter > 20 || nowPlayingIndex == 0) {
            mainController.initializeTimeSlider();
            seek(0);
        } else {
            boolean isPlaying = isPlaying();
            setNowPlaying(nowPlayingList.get(nowPlayingIndex - 1));
            if (isPlaying) {
                play();
            }
        }
    }

    public static void mute(boolean isMuted) {
        MusicPlayer.isMuted = !isMuted;
        if (mediaPlayer != null) {
            mediaPlayer.setMute(!isMuted);
        }
    }

    public static void toggleLoop() {
        isLoopActive = !isLoopActive;
    }

    public static boolean isLoopActive() {
        return isLoopActive;
    }

    public static void toggleShuffle() {

        isShuffleActive = !isShuffleActive;

        if (isShuffleActive) {
            Collections.shuffle(nowPlayingList);
        } else {
            Collections.sort(nowPlayingList, (first, second) -> {
                int result = Library.getAlbum(first.getAlbum()).compareTo(Library.getAlbum(second.getAlbum()));
                if (result != 0) {
                    return result;
                }
                result = Library.getAlbum(first.getAlbum()).compareTo(Library.getAlbum(second.getAlbum()));
                if (result != 0) {
                    return result;
                }
                result = first.compareTo(second);
                return result;
            });
        }

        nowPlayingIndex = nowPlayingList.indexOf(nowPlaying);

        if (mainController.getSubViewController() instanceof NowPlayingController) {
            mainController.loadView("nowPlaying");
        }
    }

    public static boolean isShuffleActive() {
        return isShuffleActive;
    }

    public static Stage getStage() {
        return stage;
    }

    /**
     * Gets main controller object.
     * @return MainController
     */
    public static MainController getMainController() {
        return mainController;
    }

    /**
     * Gets currently playing song list.
     * @return arraylist of now playing songs
     */
    public static ArrayList<Song> getNowPlayingList() {
        return nowPlayingList == null ? new ArrayList<>() : new ArrayList<>(nowPlayingList);
    }

    public static void addSongToNowPlayingList(Song song) {
        if (!nowPlayingList.contains(song)) {
            nowPlayingList.add(song);
            Library.savePlayingList();
        }
    }

    public static void setNowPlayingList(List<Song> list) {
        nowPlayingList = new ArrayList<>(list);
        Library.savePlayingList();
    }

    /**
     * Chơi bài hát được nhập vào
     * @param song bài hát cần đc chơi
     */
    public static void setNowPlaying(Song song) {
        if (nowPlayingList.contains(song)) {

            updatePlayCount();
            nowPlayingIndex = nowPlayingList.indexOf(song);
            if (nowPlaying != null) {
                nowPlaying.setPlaying(false);
            }
            nowPlaying = song;
            nowPlaying.setPlaying(true);
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
            if (timer != null) {
                timer.cancel();
            }
            timer = new Timer();
            timerCounter = 0;
            secondsPlayed = 0;
            String path = song.getLocation();
            Media media = new Media(Paths.get(path).toUri().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.volumeProperty().bind(mainController.getVolumeSlider().valueProperty().divide(200));
            mediaPlayer.setOnEndOfMedia(new SongSkipper());
            mediaPlayer.setMute(isMuted);
            mainController.updateNowPlayingButton();
            mainController.initializeTimeSlider();
            mainController.initializeTimeLabels();
        }
    }

    private static void updatePlayCount() {
        if (nowPlaying != null) {
            int length = (int) nowPlaying.getLengthInSeconds();
            if ((100 * secondsPlayed / length) > 50) {
                nowPlaying.played();
            }
        }
    }

    public static Song getNowPlaying() {
        return nowPlaying;
    }

    public static String getTimePassed() {
        int secondsPassed = timerCounter / 4;
        int minutes = secondsPassed / 60;
        int seconds = secondsPassed % 60;
        return Integer.toString(minutes) + ":" + (seconds < 10 ? "0" + seconds : Integer.toString(seconds));
    }

    public static String getTimeRemaining() {
        long secondsPassed = timerCounter / 4;
        long totalSeconds = getNowPlaying().getLengthInSeconds();
        long secondsRemaining = totalSeconds - secondsPassed;
        long minutes = secondsRemaining / 60;
        long seconds = secondsRemaining % 60;
        return Long.toString(minutes) + ":" + (seconds < 10 ? "0" + seconds : Long.toString(seconds));
    }

    public static void setDraggedItem(Object item) {
        draggedItem = item;
    }

    public static Object getDraggedItem() {
        return draggedItem;
    }

    public static int getXMLFileNum() {
        return xmlFileNum;
    }

    public static void setXMLFileNum(int i) {
        xmlFileNum = i;
    }

    public static int getLastIdAssigned() {
        return lastIdAssigned;
    }

    public static void setLastIdAssigned(int i) {
        lastIdAssigned = i;
    }
}
