package app.musicplayer.model;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import app.musicplayer.MusicPlayer;
import app.musicplayer.util.ImportMusicTask;
import app.musicplayer.util.Resources;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class Library {

    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String ARTIST = "artist";
    private static final String ALBUM = "album";
    private static final String LENGTH = "length";
    private static final String TRACKNUMBER = "trackNumber";
    private static final String DISCNUMBER = "discNumber";
    private static final String PLAYCOUNT = "playCount";
    private static final String PLAYDATE = "playDate";
    private static final String LOCATION = "location";

    //Mảng danh sách đối tượng Song
    private static ArrayList<Song> songs;
    private static ArrayList<Artist> artists;
    private static ArrayList<Album> albums;
    private static ArrayList<Playlist> playlists;
    private static int maxProgress;
    private static ImportMusicTask<Boolean> task;

    /**
     * Tạo file xml từ folder nhạc sử dụng task để tăng tốc độ xử lí
     * @param path Đường dẫn folder nhạc
     * @param task Tiến trình thêm danh sách, đồng bộ với controller progress hiển thị
     *             % hoàn thành
     */
    public static void importMusic(String path, ImportMusicTask<Boolean> task) throws Exception {

        Library.maxProgress = 0;
        Library.task = task;

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        Element library = doc.createElement("library");
        Element musicLibrary = doc.createElement("musicLibrary");
        Element songs = doc.createElement("songs");
        Element playlists = doc.createElement("playlists");
        Element nowPlayingList = doc.createElement("nowPlayingList");

        // Adds elements to library section.
        doc.appendChild(library);
        library.appendChild(musicLibrary);
        library.appendChild(songs);
        library.appendChild(playlists);
        library.appendChild(nowPlayingList);

        // Creates sub sections for music library path, number of files, and last song id assigned.
        Element musicLibraryPath = doc.createElement("path");
        Element musicLibraryFileNum = doc.createElement("fileNum");
        Element lastIdAssigned = doc.createElement("lastId");

        // Adds music library path to xml file.
        musicLibraryPath.setTextContent(path);
        musicLibrary.appendChild(musicLibraryPath);

        int id = 0;
        File directory = new File(Paths.get(path).toUri());

        //Lấy số file để biết cần bao nhiêu file trong tiến trình
        getMaxProgress(directory);
        //Cập nhật max min trong control progress để hiển thị khi nào xong
        Library.task.updateProgress(id, Library.maxProgress);

        // Viết vào XML file và trả về số file trong folder nhạc
        int i = writeXML(directory, doc, songs, id);
        String fileNumber = Integer.toString(i);

        // Thêm số file trong folder nhạc vào xml
        musicLibraryFileNum.setTextContent(fileNumber);
        musicLibrary.appendChild(musicLibraryFileNum);

        // Tìm last id trong danh sách bài hát và thêm vào xml
        int j = i - 1;
        lastIdAssigned.setTextContent(Integer.toString(j));
        musicLibrary.appendChild(lastIdAssigned);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);

        File xmlFile = new File(Resources.JAR + "library.xml");

        StreamResult result = new StreamResult(xmlFile);
        transformer.transform(source, result);

        Library.maxProgress = 0;
        Library.task = null;
    }

    /**
     * Tính số file nhạc được hỗ trợ trong đường dẫn
     * @param directory đường dẫn folder âm nhạc
     */
    private static void getMaxProgress(File directory) {
        File[] files = directory.listFiles();

        for (File file : files) {
            if (file.isFile() && isSupportedFileType(file.getName())) {
                Library.maxProgress++;
            } else if (file.isDirectory()) {
                getMaxProgress(file);
            }
        }
    }

    /**
     * Thêm các element bài hát vào element danh sách. Mục đích lưu vào trong xml
     * @param directory Đường dẫn folder chứa nhạc
     * @param doc Trình tạo xml
     * @param songs Node Songs - Chứa danh sách thông tin các bài hát
     * @param i Cập nhật progress % hoàn thành
     * @return
     */
    private static int writeXML(File directory, Document doc, Element songs, int i) {
        File[] files = directory.listFiles();

        for (File file : files) {
            if (file.isFile() && isSupportedFileType(file.getName())) {
                try {

                    AudioFile audioFile = AudioFileIO.read(file);
                    Tag tag = audioFile.getTag();
                    AudioHeader header = audioFile.getAudioHeader();

                    Element song = doc.createElement("song");
                    songs.appendChild(song);

                    Element id = doc.createElement("id");
                    Element title = doc.createElement("title");
                    Element artist = doc.createElement("artist");
                    Element album = doc.createElement("album");
                    Element length = doc.createElement("length");
                    Element trackNumber = doc.createElement("trackNumber");
                    Element discNumber = doc.createElement("discNumber");
                    Element playCount = doc.createElement("playCount");
                    Element playDate = doc.createElement("playDate");
                    Element location = doc.createElement("location");

                    id.setTextContent(Integer.toString(i++));
                    
                    String artistTitle = null;
                    String albumSong = null;
                    String track = null;
                    String disc = null;
                    String titleSong= null;
                    if(tag==null)
                    {
                    	Path path = file.toPath();
                        String fileName = path.getFileName().toString();
                        titleSong = fileName.substring(0, fileName.lastIndexOf('.'));
                        title.setTextContent(titleSong);                                               
                       
                        album.setTextContent("Unknown Album");
                       
                        artist.setTextContent("Unknown Artist");
                    }
                    else {
                    	
                    	albumSong = tag.getFirst(FieldKey.ALBUM);
                    	track = tag.getFirst(FieldKey.TRACK);
                    	disc = tag.getFirst(FieldKey.DISC_NO);
                    	titleSong= tag.getFirst(FieldKey.TITLE);
                    	
                    	artistTitle = tag.getFirst(FieldKey.ALBUM_ARTIST);
                        if (artistTitle == null || artistTitle.equals("") || artistTitle.equals("null")) {
                            artistTitle = tag.getFirst(FieldKey.ARTIST);
                        }

                    }
                    
                      
                    if (titleSong == null || titleSong.equals("") || titleSong.equals("null")) {
                    	Path path = file.toPath();
                        String fileName = path.getFileName().toString();
                        titleSong = fileName.substring(0, fileName.lastIndexOf('.'));
                    }
                    title.setTextContent(titleSong);
                    
                    
                    artist.setTextContent(
                            (artistTitle == null || artistTitle.equals("") || artistTitle.equals("null")) ? "" : artistTitle
                    );
                    
                    album.setTextContent(albumSong);
                    length.setTextContent(Integer.toString(header.getTrackLength()));
                    
                    trackNumber.setTextContent(
                            (track == null || track.equals("") || track.equals("null")) ? "0" : track
                    );
                    
                    discNumber.setTextContent(
                            (disc == null || disc.equals("") || disc.equals("null")) ? "0" : disc
                    );
                    playCount.setTextContent("0");
                    playDate.setTextContent(LocalDateTime.now().toString());
                    location.setTextContent(Paths.get(file.getAbsolutePath()).toString());

                    song.appendChild(id);
                    song.appendChild(title);
                    song.appendChild(artist);
                    song.appendChild(album);
                    song.appendChild(length);
                    song.appendChild(trackNumber);
                    song.appendChild(discNumber);
                    song.appendChild(playCount);
                    song.appendChild(playDate);
                    song.appendChild(location);

                    task.updateProgress(i, Library.maxProgress);

                } catch (Exception ex) {

                    ex.printStackTrace();
                }

            } else if (file.isDirectory()) {

                i = writeXML(file, doc, songs, i);
            }
        }
        return i;
    }

    /**
     *  Kiểm tra có nằm trong danh sách file được hỗ trợ
     * @param fileName
     * @return
     */
    public static boolean isSupportedFileType(String fileName) {

        String extension = "";
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i+1).toLowerCase();
        }
        switch (extension) {
            // MP3
            case "mp3":
                // MP4
            case "mp4":
            case "m4a":
            case "m4v":
                // WAV
            case "wav":
                return true;
            default:
                return false;
        }
    }

    /**
     * Lấy danh sách đối tượng song (bài hát)
     * @return observable list of songs
     */
    public static ObservableList<Song> getSongs() {
        // If the observable list of songs has not been initialized.
        if (songs == null) {
            songs = new ArrayList<>();
            // Updates the songs array list.
            updateSongsList();
        }
        return FXCollections.observableArrayList(songs);
    }

    private static Song getSong(int id) {
        if (songs == null) {
            getSongs();
        }
        return songs.get(id);
    }

    public static Song getSong(String title) {
        if (songs == null) {
            getSongs();
        }
        return songs.stream().filter(song -> title.equals(song.getTitle())).findFirst().get();
    }

    /**
     * Cập nhật SongsList
     * Duyệt toàn bộ library xml file và tạo đối tượng ánh xạ Song sau đó thêm vào songsList
     */
    private static void updateSongsList() {
        try {

            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty("javax.xml.stream.isCoalescing", true);
            FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
            XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

            String element = "";
            int id = -1;
            String title = null;
            String artist = null;
            String album = null;
            Duration length = null;
            int trackNumber = -1;
            int discNumber = -1;
            int playCount = -1;
            LocalDateTime playDate = null;
            String location = null;

            while(reader.hasNext()) {
                reader.next();

                if (reader.isWhiteSpace()) {
                    continue;
                } else if (reader.isStartElement()) {
                    element = reader.getName().getLocalPart();
                } else if (reader.isCharacters()) {
                    String value = reader.getText();

                    switch (element) {
                        case ID:
                            id = Integer.parseInt(value);
                            break;
                        case TITLE:
                            title = value;
                            break;
                        case ARTIST:
                            artist = value;
                            break;
                        case ALBUM:
                            album = value;
                            break;
                        case LENGTH:
                            length = Duration.ofSeconds(Long.parseLong(value));
                            break;
                        case TRACKNUMBER:
                            trackNumber = Integer.parseInt(value);
                            break;
                        case DISCNUMBER:
                            discNumber = Integer.parseInt(value);
                            break;
                        case PLAYCOUNT:
                            playCount = Integer.parseInt(value);
                            break;
                        case PLAYDATE:
                            playDate = LocalDateTime.parse(value);
                            break;
                        case LOCATION:
                            location = value;
                            break;
                    } // End switch
                } else if (reader.isEndElement() && reader.getName().getLocalPart().equals("song")) {
                	
                    songs.add(new Song(id, title, artist, album, length, trackNumber, discNumber, playCount, playDate, location));
                    id = -1;
                    title = null;
                    artist = null;
                    album = null;
                    length = null;
                    trackNumber = -1;
                    discNumber = -1;
                    playCount = -1;
                    playDate = null;
                    location = null;

                } else if (reader.isEndElement() && reader.getName().getLocalPart().equals("songs")) {

                    reader.close();
                    break;
                }
            } // End while

            reader.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Lấy ra albums arraylist
     * Duyệt toàn bộ library xml file và tạo đối tượng ánh xạ album sau đó thêm vào albums arraylist
     */
    public static ObservableList<Album> getAlbums() {
        // If the observable list of albums has not been initialized.
        if (albums == null) {
            if (songs == null) {
                //Nếu danh sách trống thì cập nhật lại danh sách bài hát
                getSongs();
            }
            // Updates the albums array list.
            updateAlbumsList();
        }
        return FXCollections.observableArrayList(albums);
    }

    public static Album getAlbum(String title) {
        if (albums == null) {
            getAlbums();
        }
        return albums.stream().filter(album -> title.equals(album.getTitle())).findFirst().get();
    }

    /**
     * Cập nhật albums arraylist
     * Dựa vào songs array list, tạo group by bộ dữ liệu album title artist
     */
    private static void updateAlbumsList() {
        albums = new ArrayList<>();

        TreeMap<String, List<Song>> albumMap = new TreeMap<>(
                songs.stream()
                        .filter(song -> song.getAlbum() != null)
                        .collect(Collectors.groupingBy(Song::getAlbum))
        );

        int id = 0;

        for (Map.Entry<String, List<Song>> entry : albumMap.entrySet()) {
            ArrayList<Song> songs = new ArrayList<>();

            songs.addAll(entry.getValue());

            TreeMap<String, List<Song>> artistMap = new TreeMap<>(
                    songs.stream()
                            .filter(song -> song.getArtist() != null)
                            .collect(Collectors.groupingBy(Song::getArtist))
            );

            for (Map.Entry<String, List<Song>> e : artistMap.entrySet()) {
                ArrayList<Song> albumSongs = new ArrayList<>();
                String artist = e.getValue().get(0).getArtist();

                albumSongs.addAll(e.getValue());

                albums.add(new Album(id++, entry.getKey(), artist, albumSongs));
            }
        }
    }

    /**
     * Lấy danh sách ObservableList  Artist, ObservableList dùng để
     * chèn vào các cell table, grid ...
     * @return observable list of artists
     */
    public static ObservableList<Artist> getArtists() {
        if (artists == null) {
            if (albums == null) {
                getAlbums();
            }
            // Cập nhật danh sách Artist
            updateArtistsList();
        }
        return FXCollections.observableArrayList(artists);
    }

    public static Artist getArtist(String title) {
        if (artists == null) {
            getArtists();
        }
        return artists.stream().filter(artist -> title.equals(artist.getTitle())).findFirst().get();
    }

    /**
     * Cập nhật danh sách artists
     * Dùng filter để lọc ra danh sách artist dựa vào danh sách album
     */
    private static void updateArtistsList() {
        artists = new ArrayList<>();

        TreeMap<String, List<Album>> artistMap = new TreeMap<>(
                albums.stream()
                        .filter(album -> album.getArtist() != null)
                        .collect(Collectors.groupingBy(Album::getArtist))
        );

        for (Map.Entry<String, List<Album>> entry : artistMap.entrySet()) {

            ArrayList<Album> albums = new ArrayList<>();

            albums.addAll(entry.getValue());

            artists.add(new Artist(entry.getKey(), albums));
        }
    }

    public static void addPlaylist(String text) {

        Thread thread = new Thread(() -> {

            int i = playlists.size() - 2;
            playlists.add(new Playlist(i, text, new ArrayList<>()));

            try {
                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                Document doc = docBuilder.parse(Resources.JAR + "library.xml");

                XPathFactory xPathfactory = XPathFactory.newInstance();
                XPath xpath = xPathfactory.newXPath();

                XPathExpression expr = xpath.compile("/library/playlists");
                Node playlists = ((NodeList) expr.evaluate(doc, XPathConstants.NODESET)).item(0);

                Element playlist = doc.createElement("playlist");
                playlist.setAttribute("id", Integer.toString(i));
                playlist.setAttribute(TITLE, text);
                playlists.appendChild(playlist);

                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                DOMSource source = new DOMSource(doc);
                File xmlFile = new File(Resources.JAR + "library.xml");
                StreamResult result = new StreamResult(xmlFile);
                transformer.transform(source, result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        });

        thread.start();
    }

    public static void removePlaylist(Playlist playlist) {
        playlists.remove(playlist);
    }

    /**
     * Lấy ra danh sách những playlist(ds vừa nghe, ds nghe nhiều nhất, ds bạn tạo
     * trong library xml
     * @return Danh sách ObservableList<Playlist>
     */
    public static ObservableList<Playlist> getPlaylists() {
        if (playlists == null) {

            playlists = new ArrayList<>();
            int id = 0;

            try {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                factory.setProperty("javax.xml.stream.isCoalescing", true);
                FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
                XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

                String element;
                boolean isPlaylist = false;
                String title = null;
                ArrayList<Song> songs = new ArrayList<>();

                while(reader.hasNext()) {
                    reader.next();
                    if (reader.isWhiteSpace()) {
                        continue;
                    } else if (reader.isStartElement()) {
                        element = reader.getName().getLocalPart();

                        // Nếu element là một playlist, đọc giá trị của element là playlist id và tên playlist
                        if (element.equals("playlist")) {
                            isPlaylist = true;

                            id = Integer.parseInt(reader.getAttributeValue(0));
                            title = reader.getAttributeValue(1);
                        }
                    } else if (reader.isCharacters() && isPlaylist) {
                        // Nhận giá trị reader (song ID), lấy bài hát theo id vào thêm vào danh sách bài hát
                        String value = reader.getText();
                        songs.add(getSong(Integer.parseInt(value)));
                    } else if (reader.isEndElement() && reader.getName().getLocalPart().equals("playlist")) {
                        // Nếu các thông tin playlist (playlist id, tên và danh sách bài hát) đã có,
                        // tạo ra một playlist mới và thêm vào danh sách playlists
                        playlists.add(new Playlist(id, title, songs));
                        id = -1;
                        title = null;
                        songs = new ArrayList<>();
                    } else if (reader.isEndElement() && reader.getName().getLocalPart().equals("playlists")) {
                        reader.close();
                        break;
                    }
                }
                reader.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            //Sắp xếp lại danh sách theo thứ tự id
            playlists.sort((x, y) -> {
                if (x.getId() < y.getId()) {
                    return 1;
                } else if (x.getId() > y.getId()) {
                    return -1;
                } else {
                    return 0;
                }
            });

            //Tạo most played playlist và recently played playlist
            playlists.add(new MostPlayedPlaylist(-2));
            playlists.add(new RecentlyPlayedPlaylist(-1));
        } else {
            playlists.sort((x, y) -> {
                if (x.getId() < y.getId()) {
                    return 1;
                } else if (x.getId() > y.getId()) {
                    return -1;
                } else {
                    return 0;
                }
            });
        }
        return FXCollections.observableArrayList(playlists);
    }

    public static Playlist getPlaylist(int id) {
        if (playlists == null) {
            getPlaylists();
        }
        // Gets the play list size.
        int playListSize = Library.getPlaylists().size();
        // The +2 takes into account the two default play lists.
        // The -1 is used because size() starts at 1 but indexes start at 0.
        return playlists.get(playListSize - (id + 2) - 1);
    }

    public static Playlist getPlaylist(String title) {
        if (playlists == null) {
            getPlaylists();
        }
        return playlists.stream().filter(playlist -> title.equals(playlist.getTitle())).findFirst().get();
    }

    /**
     * Lấy danh sách bài hát đang được chơi
     * @return  ArrayList<Song>
     */
    public static ArrayList<Song> loadPlayingList() {

        ArrayList<Song> nowPlayingList = new ArrayList<>();

        try {

            XMLInputFactory factory = XMLInputFactory.newInstance();
            FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
            XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

            String element = "";
            boolean isNowPlayingList = false;

            while(reader.hasNext()) {
                reader.next();
                if (reader.isWhiteSpace()) {
                    continue;
                } else if (reader.isCharacters() && isNowPlayingList) {
                    String value = reader.getText();
                    if (element.equals(ID)) {
                        nowPlayingList.add(getSong(Integer.parseInt(value)));
                    }
                } else if (reader.isStartElement()) {
                    element = reader.getName().getLocalPart();
                    if (element.equals("nowPlayingList")) {
                        isNowPlayingList = true;
                    }
                } else if (reader.isEndElement() && reader.getName().getLocalPart().equals("nowPlayingList")) {
                    reader.close();
                    break;
                }
            }

            reader.close();

        } catch (Exception ex) {

            ex.printStackTrace();
        }

        return nowPlayingList;
    }

    public static void savePlayingList() {

        Thread thread = new Thread(() -> {

            try {

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                Document doc = docBuilder.parse(Resources.JAR + "library.xml");

                XPathFactory xPathfactory = XPathFactory.newInstance();
                XPath xpath = xPathfactory.newXPath();

                XPathExpression expr = xpath.compile("/library/nowPlayingList");
                Node playingList = ((NodeList) expr.evaluate(doc, XPathConstants.NODESET)).item(0);

                NodeList nodes = playingList.getChildNodes();
                while (nodes.getLength() > 0) {
                    playingList.removeChild(nodes.item(0));
                }

                for (Song song : MusicPlayer.getNowPlayingList()) {
                    Element id = doc.createElement(ID);
                    id.setTextContent(Integer.toString(song.getId()));
                    playingList.appendChild(id);
                }

                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                DOMSource source = new DOMSource(doc);
                File xmlFile = new File(Resources.JAR + "library.xml");
                StreamResult result = new StreamResult(xmlFile);
                transformer.transform(source, result);

            } catch (Exception ex) {

                ex.printStackTrace();
            }

        });

        thread.start();
    }
}
