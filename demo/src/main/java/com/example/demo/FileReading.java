package com.example.demo;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import static java.nio.file.StandardWatchEventKinds.*;

public class FileReading {

	public static void main(String[] args) {
		try {
			// Define the path to the folder you want to monitor
			Path folderPath = Paths.get("C:\\ProgramData\\Jenkins\\.jenkins\\users");

			// Create a WatchService
			WatchService watchService = FileSystems.getDefault().newWatchService();

			// Register the directory to monitor ENTRY_CREATE (new files)
			folderPath.register(watchService, ENTRY_CREATE);

			System.out.println("Monitoring folder for new files...");

			// Set up the database connection
			Connection connection = DriverManager.getConnection(
					"jdbc:postgresql://localhost:5432/xml", "postgres", "1234");

			// Loop to keep the watcher alive
			while (true) {
				// Retrieve and remove the next watch key, waiting if none are present
				WatchKey key = watchService.take();

				for (WatchEvent<?> event : key.pollEvents()) {
					// Get the type of event (e.g., ENTRY_CREATE)
					WatchEvent.Kind<?> kind = event.kind();

					// Check if a new file was created
					if (kind == ENTRY_CREATE) {
						// Get the file name from the event
						WatchEvent<Path> ev = (WatchEvent<Path>) event;
						Path fileName = ev.context();

						// Check if the new file is a .xml file
						if (fileName.toString().endsWith(".xml")) {
							// Process the XML file and save to the database
							File xmlFile = folderPath.resolve(fileName).toFile();
							saveUserMappingsToDatabase(xmlFile, connection);
						}
					}
				}

				// Reset the key -- this step is critical to receive further watch events
				boolean valid = key.reset();
				if (!valid) {
					break; // Exit loop if the key is no longer valid (e.g., directory deleted)
				}
			}

		} catch (IOException | InterruptedException | SQLException e) {
			e.printStackTrace();
		}
	}

	// Method to parse the XML file and save the data to PostgreSQL
	public static void saveUserMappingsToDatabase(File xmlFile, Connection connection) {
		try {
			// Parse the XML file
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(xmlFile);

			// Get the root element (hudson.model.UserIdMapper)
			Element root = document.getDocumentElement();

			// Find the idToDirectoryNameMap element
			NodeList entries = root.getElementsByTagName("entry");

			// Loop through each <entry> in idToDirectoryNameMap
			for (int i = 0; i < entries.getLength(); i++) {
				Node entry = entries.item(i);

				if (entry.getNodeType() == Node.ELEMENT_NODE) {
					Element entryElement = (Element) entry;

					// Get the two <string> elements in each <entry>
					NodeList strings = entryElement.getElementsByTagName("string");

					if (strings.getLength() == 2) {
						// Extract the userId and directoryName
						String userId = strings.item(0).getTextContent();
						String directoryName = strings.item(1).getTextContent();

						// Insert the userId and directoryName into PostgreSQL
						String sql = "INSERT INTO user_directory_mapping (user_id, directory_name) VALUES (?, ?) " +
								"ON CONFLICT (user_id) DO UPDATE SET directory_name = EXCLUDED.directory_name";
						PreparedStatement statement = connection.prepareStatement(sql);
						statement.setString(1, userId);
						statement.setString(2, directoryName);

						// Execute the SQL statement
						statement.executeUpdate();
						System.out.println("Inserted/Updated mapping: " + userId + " -> " + directoryName);
					}
				}
			}

		} catch (ParserConfigurationException | SAXException | IOException | SQLException e) {
			e.printStackTrace();
		}
	}
}
