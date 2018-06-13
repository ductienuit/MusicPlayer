package app.musicplayer.util;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;

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
import app.musicplayer.model.Library;
import app.musicplayer.model.Song;

public class XMLEditor {
	
	private static String musicDirectory;

	// Bắt đầu bằng xml thì nó là dữ liệu trong file xml
	// Khởi tạo array lists để lưu tên file của bài hát trong xml
	// Mảng này sẽ được kiểm tra nếu bất cứ bài hát nào được thêm hoặc xóa trong folder âm nhạc
	private static ArrayList<String> xmlSongsFileNames = new ArrayList<>();
	// Lưu đường dẫn đến bài hát
	// Nếu bạn xóa một bài hát khỏi xml file cũng như dùng để tìm node và remove.
	private static ArrayList<String> xmlSongsFilePaths = new ArrayList<>();

	//Khởi tạo mảng danh sách lưu tên file bài hát trong thư mục nhạc
	//Mảng danh sách sẽ kiểm tra nếu một bài hát được thêm hoặc xóa trong thư mục nhạc
	private static ArrayList<String> musicDirFileNames = new ArrayList<>();
	// Lưu files ánh xạ trong thư mục bài hát
	// Danh sách lưu file bài hát trong thư mục nhạc
	private static ArrayList<File> musicDirFiles = new ArrayList<>();
	

	// Danh sách với những file nhạc cần được thêm
	private static ArrayList<File> songFilesToAdd = new ArrayList<>();
	
	// Initializes array list with song paths of songs to be deleted from library.xml
	private static ArrayList<String> songPathsToDelete = new ArrayList<>();

	// Danh sách những bài hát cần được thêm sau khi khởi tạo đối tượng
	private static ArrayList<Song> songsToAdd = new ArrayList<>();
	
	// Initializes booleans used to determine how the library.xml file needs to be edited.
	private static boolean addSongs;
	private static boolean deleteSongs;

	public static ArrayList<Song> getNewSongs() { return songsToAdd; }

	public static void setMusicDirectory(Path musicDirectoryPath) {
		musicDirectory = musicDirectoryPath.toString();
	}

	/**
	 *
	 */
	public static void addDeleteChecker() {
		// Tìm tên các filename (file nhạc) trong library xml file và lưu chúng
		// vào ArrayList xmlSongsFileNames
		xmlSongsFilePathFinder();

		// Tìm tên file bài hát trong folder nhạc và lưu chúng vào danh sách musicDirFiles,musicDirFileNames arraylist
		musicDirFileFinder(new File(musicDirectory));
							
		//Khởi tạo biến đếm để xác định chính xác bài hát nào cần được thêm hoặc xóa
		int i = 0;

		// Lặp trong musicDirFiles và kiểm tra bài hát đã có trong library.xml
		// Nếu không thì thêm tệp vào xml
		for (String songFileName : musicDirFileNames) {
			// Nếu file nhạc không có trong xmlSongsFilenames, thêm file nhạc vào xml
			if (!xmlSongsFileNames.contains(songFileName)) {
				// Thêm file nhạc cần vào mảng file nhạc cần thêm.
				songFilesToAdd.add(musicDirFiles.get(i));
				addSongs = true;
			}
			i++;
		}
		

		int j = 0;
		// Lặp xmlSongsFileNames và kiểm tra nếu tất cả bài hát trong xml có trong folder nhạc
		// Nếu một hoặc nhiều bài hát của xml không có trong folder nhạc thì sẽ xóa.
		for (String songFileName : xmlSongsFileNames) {
			// Nếu songFileName không có trong musicDirFileNames thì thêm nó
			// vào danh sách cần xóa songPathsToDelete
			if (!musicDirFileNames.contains(songFileName)) {
				// Thêm bài hát cần xóa vào mảng cần xóa
				songPathsToDelete.add(xmlSongsFilePaths.get(j));
				deleteSongs = true;
			}
			j++;
		}
		
		// Nếu bài hát cần được thêm vào xml file
		if (addSongs) {	
            // Thêm những bài hát mới vào xml
			addSongToXML();
		}
		
        // Nếu bài hát cần được xóa khỏi xml file
		if (deleteSongs) {
			// Xóa bài hát từ file xml
			deleteSongFromXML();
		}
		
	}
	
	private static void xmlSongsFilePathFinder() {
		try {
			// Creates reader for xml file.
			XMLInputFactory factory = XMLInputFactory.newInstance();
			factory.setProperty("javax.xml.stream.isCoalescing", true);
			FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
			XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");
			
			String element = null;
			String songLocation;
			
			// Loops through xml file looking for song titles.
			// Stores the song title in the xmlSongsFileNames array list.
			while(reader.hasNext()) {
			    reader.next();
			    if (reader.isWhiteSpace()) {
			        continue;
			    } else if (reader.isStartElement()) {
			    	element = reader.getName().getLocalPart();
			    } else if (reader.isCharacters() && element.equals("location")) {
			    	// Lấy vị trí bài hát và thêm vào arraylist danh sách địa chỉ bài hát
			    	songLocation = reader.getText();
			    	xmlSongsFilePaths.add(songLocation);
			    	
			    	// Lấy tên của bài hát baihat.mp3 "baihat" bỏ vào xmlSongsFileNames array list.
			    	int i = songLocation.lastIndexOf("\\");
			    	String songFileName = songLocation.substring(i + 1, songLocation.length());
			    	xmlSongsFileNames.add(songFileName);
			    }
			}
			// Closes xml reader.
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Hàm duyệt tất cả đường dẫn file nhạc trong folder nhạc và lưu chúng vào mảng musicDirectoryFile
	 * @param musicDirectoryFile
	 */
	private static void musicDirFileFinder(File musicDirectoryFile) {
		// Duyệt tất cả đường dẫn file nhạc trong folder nhạc và lưu chúng vào mảng musicDirectoryFile
        File[] files = musicDirectoryFile.listFiles();

        for (File file : files) {
            if (file.isFile() && Library.isSupportedFileType(file.getName())) {
            	// Adds the file to the musicDirFiles array list.
				// Thêm đường dẫn file vào arraylist musicDirFiles
            	musicDirFiles.add(file);

				// Thêm tên của file nhạc vào musicDirFileNames array list.
            	musicDirFileNames.add(file.getName());
            } else if (file.isDirectory()) {
            	musicDirFileFinder(file);
            }
        }
	}

	/**
	 * Thêm bài hát mới trong folder nhạc vào file xml
	 */
	private static void addSongToXML() {
		// Khởi tạo mảng danh sách những đối tượng songsToAdd dựa vào danh sách file bài hát
		// cần được thêm
		createNewSongObject();
		
		if (songsToAdd.size() == 0) {
			System.out.println("Khong bai hat nao can dc them vao");
			return;
		}
		
        try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(Resources.JAR + "library.xml");
			
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

			// Tạo node danh sách bài hát songs
            XPathExpression expr = xpath.compile("/library/songs");
            Node songsNode = ((NodeList) expr.evaluate(doc, XPathConstants.NODESET)).item(0);

            for (Song song : songsToAdd) {
                // Khởi tạo các element chứa thông tin bài hát
                Element newSong = doc.createElement("song");
                Element newSongId = doc.createElement("id");
                Element newSongTitle = doc.createElement("title");
                Element newSongArtist = doc.createElement("artist");
                Element newSongAlbum = doc.createElement("album");
                Element newSongLength = doc.createElement("length");
                Element newSongTrackNumber = doc.createElement("trackNumber");
                Element newSongDiscNumber = doc.createElement("discNumber");
                Element newSongPlayCount = doc.createElement("playCount");
                Element newSongPlayDate = doc.createElement("playDate");
                Element newSongLocation = doc.createElement("location");

                // Lưu bài hát mới
                newSongId.setTextContent(Integer.toString(song.getId()));
                newSongTitle.setTextContent(song.getTitle());
                newSongArtist.setTextContent(song.getArtist());
                newSongAlbum.setTextContent(song.getAlbum());
                newSongLength.setTextContent(Long.toString(song.getLengthInSeconds()));
                newSongTrackNumber.setTextContent(Integer.toString(song.getTrackNumber()));
                newSongDiscNumber.setTextContent(Integer.toString(song.getDiscNumber()));
                newSongPlayCount.setTextContent(Integer.toString(song.getPlayCount()));
                newSongPlayDate.setTextContent(song.getPlayDate().toString());
                newSongLocation.setTextContent(song.getLocation());
                
                // Thêm bài hát mới vào node danh sách bài hát
                songsNode.appendChild(newSong);
                // Thêm dữ liệu bài hát cho bài hát mới.
                newSong.appendChild(newSongId);
                newSong.appendChild(newSongTitle);
                newSong.appendChild(newSongArtist);
                newSong.appendChild(newSongAlbum);
                newSong.appendChild(newSongLength);
                newSong.appendChild(newSongTrackNumber);
                newSong.appendChild(newSongDiscNumber);
                newSong.appendChild(newSongPlayCount);
                newSong.appendChild(newSongPlayDate);
                newSong.appendChild(newSongLocation);
            }
            
            // Calculates the new xml file number, taking into account the new songs.
			// Tính toán tổng số bài hát sau khi thêm
            int newXMLFileNum = MusicPlayer.getXMLFileNum() + songFilesToAdd.size();

            // Tạo node cập nhật file number
            expr = xpath.compile("/library/musicLibrary/fileNum");
            Node fileNum = ((NodeList) expr.evaluate(doc, XPathConstants.NODESET)).item(0);

			// Cập nhật cột fileNum trong xml file
            fileNum.setTextContent(Integer.toString(newXMLFileNum));
            // Cập nhật xmlFileNum trong MusicPlayer.
            MusicPlayer.setXMLFileNum(newXMLFileNum);

			// Lấy giá trị last id trước khi thêm tất cả các file nhạc mới
            int newLastIdAssigned = songsToAdd.get(songsToAdd.size() - 1).getId();
            
            // Tạo node lastID trong xml
            expr = xpath.compile("/library/musicLibrary/lastId");
            Node lastId = ((NodeList) expr.evaluate(doc, XPathConstants.NODESET)).item(0);
            
            // Cập nhật lastId trong xml
            lastId.setTextContent(Integer.toString(newLastIdAssigned));
            // Cập nhật lastId trong MusicPlayer.
        	MusicPlayer.setLastIdAssigned(newLastIdAssigned);
            
            
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
			System.out.println("XMLEditor: Khong tao duoc file library.xml");
		}
	}

	/**
	 * Tạo một đối tượng Song với các thuộc tính
	 * id, title, artist, album, length, trackNumber, discNumber, playCount, playDate, location
	 */
	private static void createNewSongObject() {
		
		// Searches the xml file to get the last id assigned.
		int lastIdAssigned = xmlLastIdAssignedFinder();
		
		// Loops through each song file that needs to be added and creates a song object for each.
		// Each song object is added to an array list and returned so that they can be added to the xml file.
		for (File songFile : songFilesToAdd) {
	        try {
	            AudioFile audioFile = AudioFileIO.read(songFile);
	            Tag tag = audioFile.getTag();
	            AudioHeader header = audioFile.getAudioHeader();
	            
	            // Gets song properties.
	            int id = ++lastIdAssigned;
	            String title = tag.getFirst(FieldKey.TITLE);
	            // Gets the artist, empty string assigned if song has no artist.
	            String artistTitle = tag.getFirst(FieldKey.ALBUM_ARTIST);
	            if (artistTitle == null || artistTitle.equals("") || artistTitle.equals("null")) {
	                artistTitle = tag.getFirst(FieldKey.ARTIST);
	            }
	            String artist = (artistTitle == null || artistTitle.equals("") || artistTitle.equals("null")) ? "" : artistTitle;
	            String album = tag.getFirst(FieldKey.ALBUM);
	            // Gets the track length (as an int), converts to long and saves it as a duration object.                
	            Duration length = Duration.ofSeconds((long) header.getTrackLength());
	            // Gets the track number and converts to an int. Assigns 0 if a track number is null.
	            String track = tag.getFirst(FieldKey.TRACK);                
	            int trackNumber = Integer.parseInt((track == null || track.equals("") || track.equals("null")) ? "0" : track);
	            // Gets disc number and converts to int. Assigns 0 if the disc number is null.
	            String disc = tag.getFirst(FieldKey.DISC_NO);
	            int discNumber = Integer.parseInt((disc == null || disc.equals("") || disc.equals("null")) ? "0" : disc);
	            int playCount = 0;
	            LocalDateTime playDate = LocalDateTime.now();
	            String location = Paths.get(songFile.getAbsolutePath()).toString();
	            
	            // Creates a new song object for the added song and adds it to the newSongs array list.
	            Song newSong = new Song(id, title, artist, album, length, trackNumber, discNumber, playCount, playDate, location);

	            // Adds the new song to the songsToAdd array list.
	            songsToAdd.add(newSong);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		// Updates the lastIdAssigned in MusicPlayer to account for the new songs.
		MusicPlayer.setLastIdAssigned(lastIdAssigned);
	}

	/**
	 * Tìm giá trị lastID trong file library xml
	 * * @return
	 */
    private static int xmlLastIdAssignedFinder() {
		try {
			// Creates reader for xml file.
			XMLInputFactory factory = XMLInputFactory.newInstance();
			factory.setProperty("javax.xml.stream.isCoalescing", true);
			FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
			XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");
			
			String element = null;
			String lastId = null;
			
			// Loops through xml file looking for the music directory file path.
			while(reader.hasNext()) {
			    reader.next();
			    if (reader.isWhiteSpace()) {
			        continue;
			    } else if (reader.isStartElement()) {
			    	element = reader.getName().getLocalPart();
			    } else if (reader.isCharacters() && element.equals("lastId")) {
			    	lastId = reader.getText();               	
			    	break;
			    }
			}
			// Closes xml reader.
			reader.close();
			
			// Converts the file number to an int and returns the value. 
			return Integer.parseInt(lastId);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
    }
	
	private static void deleteSongFromXML() {
		// Lấy currentXMLFileNum.
		int currentXMLFileNum = MusicPlayer.getXMLFileNum();

        try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(Resources.JAR + "library.xml");
			
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            
            // Lấy lastID trong file xml
            int xmlLastIdAssigned = xmlLastIdAssignedFinder();

            // Finds the song node corresponding to the last assigned id.
			// Tìm node bài hát trong xml dựa vào lastid
            XPathExpression expr = xpath.compile("/library/songs/song[id/text() = \"" + xmlLastIdAssigned + "\"]");
            Node lastSongNode = ((NodeList) expr.evaluate(doc, XPathConstants.NODESET)).item(0);
            
            // Loops songPathsToDelete array list và xóa những bài hát có đường dẫn giống
			// với giá trị trong songPathsToDelte

            Node deleteSongNode = null;
            for (String songFilePath : songPathsToDelete) {
                // Tìm node dựa vào location
            	expr = xpath.compile("/library/songs/song[location/text() = \"" + songFilePath + "\"]");
                deleteSongNode = ((NodeList) expr.evaluate(doc, XPathConstants.NODESET)).item(0);
                
                // Xóa node bài hát
                deleteSongNode.getParentNode().removeChild(deleteSongNode);

            	// Giảm fileNum -  tổng số bài hát hiện tại
                currentXMLFileNum--;
            }

			// Nếu lastNode bị xóa và cũng là node bài hát cuối cùng thì ta tìm
			// lastIdAssignId mới và cập nhật lại trong MusicPlayer và xml file
            if (deleteSongNode == lastSongNode) {
            	int newLastIdAssigned = xmlNewLastIdAssignedFinder();

                // Creates node to update xml last id assigned.
                expr = xpath.compile("/library/musicLibrary/lastId");
                Node lastId = ((NodeList) expr.evaluate(doc, XPathConstants.NODESET)).item(0);
                
                // Updates the lastId in MusicPlayer and in the xml file.
            	MusicPlayer.setLastIdAssigned(newLastIdAssigned);
                lastId.setTextContent(Integer.toString(newLastIdAssigned));
            }
            
            // Tạo node update xml file number.
            XPathExpression fileNumExpr = xpath.compile("/library/musicLibrary/fileNum");
            Node fileNum = ((NodeList) fileNumExpr.evaluate(doc, XPathConstants.NODESET)).item(0);

			// Cập nhật fileNum trong MusicPlayer và trong xml file
            MusicPlayer.setXMLFileNum(currentXMLFileNum);
            fileNum.setTextContent(Integer.toString(currentXMLFileNum));
                    
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
			System.out.println("Khong bai hat nao can xoa");
		}
	}

	/**
	 * Lấy lastId mới
	 */
    private static int xmlNewLastIdAssignedFinder() {
		try {
			// Creates reader for xml file.
			XMLInputFactory factory = XMLInputFactory.newInstance();
			factory.setProperty("javax.xml.stream.isCoalescing", true);
			FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
			XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");
			
			String element = null;
			String location;
			
			String currentSongId = null;
			String xmlNewLastIdAssigned = null;
			
			// Loops through xml file looking for the music directory file path.
			while(reader.hasNext()) {
			    reader.next();
			    if (reader.isWhiteSpace()) {
			        continue;
			    } else if (reader.isStartElement()) {
			    	element = reader.getName().getLocalPart();
			    } else if (reader.isCharacters() && element.equals("id")) {
			    	currentSongId = reader.getText();
			    } else if (reader.isCharacters() && element.equals("location")) {
			    	location = reader.getText();
			    	// If the current location is does not correspond to one of the files to be deleted,
			    	// then the current id is assigned as the newLastIdAssigned.
			    	if (!songPathsToDelete.contains(location)) {
			    		xmlNewLastIdAssigned = currentSongId;
			    	}
			    } else if (reader.isEndElement() && reader.getName().getLocalPart().equals("songs")) {
			    	break;
			    }
			}
			// Closes xml reader.
			reader.close();
			
			// Converts the file number to an int and returns the value. 
			return Integer.parseInt(xmlNewLastIdAssigned);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
    }
	
	public static void deleteSongFromPlaylist(int selectedPlayListId, int selectedSongId) {
        try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(Resources.JAR + "library.xml");
			
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            
            // Finds the node with the song id for the selected song in the selected play list for removal.
            String query = "/library/playlists/playlist[@id='" + selectedPlayListId + "']/songId[text() = '" + selectedSongId + "']";
            XPathExpression expr = xpath.compile(query);
            Node deleteSongNode = (Node) expr.evaluate(doc, XPathConstants.NODE);
            
            // Removes the node corresponding to the selected song.
            deleteSongNode.getParentNode().removeChild(deleteSongNode);
                    
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
	}
	
	public static void deletePlaylistFromXML(int selectedPlayListId) {		
        try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(Resources.JAR + "library.xml");
			
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            
            // Finds the node with the play list id for removal.
            String query = "/library/playlists/playlist[@id='" + selectedPlayListId + "']";
            XPathExpression expr = xpath.compile(query);
            Node deleteplaylistNode = (Node) expr.evaluate(doc, XPathConstants.NODE);
            
            // Removes the node corresponding to the selected song.
            deleteplaylistNode.getParentNode().removeChild(deleteplaylistNode);
                    
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
	}
}
