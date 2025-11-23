package org.vericrop.gui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.MainApp;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.dao.MessageDao;
import org.vericrop.gui.dao.ParticipantDao;
import org.vericrop.gui.models.Message;
import org.vericrop.gui.models.Participant;
import org.vericrop.gui.models.User;
import org.vericrop.gui.services.AuthenticationService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controller for the contacts/participants view.
 * Displays available contacts and enables messaging between participants.
 */
public class ContactsViewController {
    private static final Logger logger = LoggerFactory.getLogger(ContactsViewController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
    private static final int REFRESH_INTERVAL_MS = 5000; // 5 seconds
    
    @FXML private ListView<Participant> contactsListView;
    @FXML private Label onlineCountLabel;
    
    @FXML private VBox emptyStatePane;
    @FXML private VBox contactDetailPane;
    
    @FXML private Label contactDisplayName;
    @FXML private Label contactUsername;
    @FXML private Label contactRole;
    @FXML private Label contactStatus;
    @FXML private Label contactLastSeen;
    
    @FXML private VBox messagesContainer;
    @FXML private TextField messageSubjectField;
    @FXML private TextArea messageBodyField;
    
    private ApplicationContext appContext;
    private AuthenticationService authService;
    private ParticipantDao participantDao;
    private MessageDao messageDao;
    private Participant selectedContact;
    private User currentUser;
    private Timer refreshTimer;
    private String currentUsername;
    
    @FXML
    public void initialize() {
        logger.info("ContactsViewController initialized");
        
        // Get application context
        appContext = ApplicationContext.getInstance();
        authService = appContext.getAuthenticationService();
        participantDao = appContext.getParticipantDao();
        messageDao = appContext.getMessageDao();
        
        // Check authentication
        if (!authService.isAuthenticated()) {
            logger.warn("User not authenticated, redirecting to login");
            Platform.runLater(() -> {
                MainApp mainApp = MainApp.getInstance();
                if (mainApp != null) {
                    mainApp.switchToScreen("login.fxml");
                }
            });
            return;
        }
        
        // Get current user from database
        currentUsername = authService.getCurrentUser();
        Optional<User> userOpt = appContext.getUserDao().findByUsername(currentUsername);
        if (userOpt.isEmpty()) {
            logger.error("Current user not found in database");
            Platform.runLater(() -> {
                MainApp mainApp = MainApp.getInstance();
                if (mainApp != null) {
                    mainApp.switchToScreen("login.fxml");
                }
            });
            return;
        }
        currentUser = userOpt.get();
        
        // Set up contacts list custom cell factory
        contactsListView.setCellFactory(lv -> new ContactListCell());
        
        // Add selection listener
        contactsListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> displayContact(newVal)
        );
        
        // Load contacts
        loadContacts();
        
        // Start auto-refresh timer
        startAutoRefresh();
    }
    
    /**
     * Load contacts from database
     */
    private void loadContacts() {
        Task<List<Participant>> loadTask = new Task<>() {
            @Override
            protected List<Participant> call() {
                // Exclude current user's participants if they have any registered
                return participantDao.getActiveParticipants(null);
            }
        };
        
        loadTask.setOnSucceeded(event -> {
            List<Participant> contacts = loadTask.getValue();
            ObservableList<Participant> contactsList = FXCollections.observableArrayList(contacts);
            contactsListView.setItems(contactsList);
            
            // Update online count
            long onlineCount = contacts.stream().filter(Participant::isOnline).count();
            onlineCountLabel.setText(onlineCount + " online");
            
            logger.debug("Loaded {} contacts ({} online)", contacts.size(), onlineCount);
        });
        
        loadTask.setOnFailed(event -> {
            logger.error("Failed to load contacts", loadTask.getException());
            showError("Failed to load contacts", "Could not retrieve contacts from the server.");
        });
        
        new Thread(loadTask).start();
    }
    
    /**
     * Display contact details in the right pane
     */
    private void displayContact(Participant contact) {
        if (contact == null) {
            emptyStatePane.setVisible(true);
            emptyStatePane.setManaged(true);
            contactDetailPane.setVisible(false);
            contactDetailPane.setManaged(false);
            selectedContact = null;
            return;
        }
        
        selectedContact = contact;
        
        // Show contact details
        emptyStatePane.setVisible(false);
        emptyStatePane.setManaged(false);
        contactDetailPane.setVisible(true);
        contactDetailPane.setManaged(true);
        
        // Populate contact details
        contactDisplayName.setText(contact.getDisplayName());
        contactUsername.setText(contact.getUsername());
        contactRole.setText(contact.getUserRole());
        
        // Status with color indicator
        String statusText = contact.isOnline() ? "🟢 Online" : "⚫ Offline";
        contactStatus.setText(statusText);
        
        // Format last seen
        if (contact.getLastSeen() != null) {
            contactLastSeen.setText("Last seen: " + contact.getLastSeen().format(DATE_FORMATTER));
        } else {
            contactLastSeen.setText("Last seen: Never");
        }
        
        // Clear message fields
        messageSubjectField.clear();
        messageBodyField.clear();
        
        // Clear messages container
        messagesContainer.getChildren().clear();
        Label noMessagesLabel = new Label("Click 'Load Messages' to view conversation history");
        noMessagesLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #999; -fx-padding: 20;");
        messagesContainer.getChildren().add(noMessagesLabel);
        
        logger.debug("Displaying contact: {}", contact.getDisplayName());
    }
    
    /**
     * Load message history with selected contact
     */
    @FXML
    private void handleLoadMessages() {
        if (selectedContact == null) {
            return;
        }
        
        Task<List<Message>> loadTask = new Task<>() {
            @Override
            protected List<Message> call() {
                Long contactUserId = selectedContact.getUserId();
                List<Message> allMessages = new java.util.ArrayList<>();
                
                // Get sent messages to this contact
                allMessages.addAll(messageDao.getSentMessages(currentUser.getId()).stream()
                    .filter(m -> m.getRecipientId().equals(contactUserId))
                    .collect(java.util.stream.Collectors.toList()));
                
                // Get received messages from this contact
                allMessages.addAll(messageDao.getInboxMessages(currentUser.getId()).stream()
                    .filter(m -> m.getSenderId().equals(contactUserId))
                    .collect(java.util.stream.Collectors.toList()));
                
                // Sort by timestamp (newest first)
                allMessages.sort((m1, m2) -> m2.getSentAt().compareTo(m1.getSentAt()));
                
                return allMessages;
            }
        };
        
        loadTask.setOnSucceeded(event -> {
            List<Message> messages = loadTask.getValue();
            displayMessages(messages);
            logger.debug("Loaded {} messages with contact {}", messages.size(), selectedContact.getDisplayName());
        });
        
        loadTask.setOnFailed(event -> {
            logger.error("Failed to load messages", loadTask.getException());
            showError("Failed to load messages", "Could not retrieve message history.");
        });
        
        new Thread(loadTask).start();
    }
    
    /**
     * Display messages in the messages container
     */
    private void displayMessages(List<Message> messages) {
        messagesContainer.getChildren().clear();
        
        if (messages.isEmpty()) {
            Label noMessagesLabel = new Label("No messages yet. Send a message below!");
            noMessagesLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #999; -fx-padding: 20;");
            messagesContainer.getChildren().add(noMessagesLabel);
            return;
        }
        
        for (Message message : messages) {
            VBox messageBox = createMessageBubble(message);
            messagesContainer.getChildren().add(messageBox);
        }
    }
    
    /**
     * Create a message bubble UI component
     */
    private VBox createMessageBubble(Message message) {
        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(10));
        bubble.setMaxWidth(500);
        
        boolean isSent = message.getSenderId().equals(currentUser.getId());
        
        // Style based on sent/received
        if (isSent) {
            bubble.setStyle("-fx-background-color: #e3f2fd; -fx-background-radius: 10; -fx-border-color: #2196F3; -fx-border-radius: 10; -fx-border-width: 1;");
            bubble.setAlignment(Pos.CENTER_RIGHT);
        } else {
            bubble.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 10; -fx-border-color: #e0e0e0; -fx-border-radius: 10; -fx-border-width: 1;");
            bubble.setAlignment(Pos.CENTER_LEFT);
        }
        
        // Subject (if present)
        if (message.getSubject() != null && !message.getSubject().trim().isEmpty()) {
            Label subjectLabel = new Label(message.getSubject());
            subjectLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
            bubble.getChildren().add(subjectLabel);
        }
        
        // Body
        Label bodyLabel = new Label(message.getBody());
        bodyLabel.setWrapText(true);
        bodyLabel.setStyle("-fx-font-size: 12px;");
        bubble.getChildren().add(bodyLabel);
        
        // Timestamp and direction
        HBox footer = new HBox(5);
        footer.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        Label directionLabel = new Label(isSent ? "You" : message.getSenderUsername());
        directionLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
        Label timestampLabel = new Label(message.getSentAt().format(DATE_FORMATTER));
        timestampLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");
        footer.getChildren().addAll(directionLabel, new Label("•"), timestampLabel);
        bubble.getChildren().add(footer);
        
        return bubble;
    }
    
    /**
     * Send message to selected contact
     */
    @FXML
    private void handleSendMessage() {
        if (selectedContact == null) {
            showError("No Contact Selected", "Please select a contact to send a message.");
            return;
        }
        
        String subject = messageSubjectField.getText().trim();
        String body = messageBodyField.getText().trim();
        
        if (body.isEmpty()) {
            showError("Message Empty", "Please enter a message to send.");
            return;
        }
        
        Task<Message> sendTask = new Task<>() {
            @Override
            protected Message call() {
                Long recipientUserId = selectedContact.getUserId();
                String messageSubject = subject.isEmpty() ? "Message from " + currentUser.getUsername() : subject;
                return messageDao.sendMessage(currentUser.getId(), recipientUserId, messageSubject, body);
            }
        };
        
        sendTask.setOnSucceeded(event -> {
            Message sentMessage = sendTask.getValue();
            if (sentMessage != null) {
                logger.info("Message sent to contact: {}", selectedContact.getDisplayName());
                showInfo("Message Sent", "Your message has been sent successfully.");
                
                // Clear message fields
                messageSubjectField.clear();
                messageBodyField.clear();
                
                // Reload messages
                handleLoadMessages();
            } else {
                showError("Send Failed", "Failed to send message. Please try again.");
            }
        });
        
        sendTask.setOnFailed(event -> {
            logger.error("Failed to send message", sendTask.getException());
            showError("Send Failed", "An error occurred while sending the message.");
        });
        
        new Thread(sendTask).start();
    }
    
    /**
     * Clear message fields
     */
    @FXML
    private void handleClearMessage() {
        messageSubjectField.clear();
        messageBodyField.clear();
    }
    
    /**
     * Refresh contacts list
     */
    @FXML
    private void handleRefresh() {
        loadContacts();
    }
    
    /**
     * Go back to previous screen
     */
    @FXML
    private void handleBack() {
        stopAutoRefresh();
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            // Determine which screen to go back to based on user role
            String role = currentUser.getRole();
            if ("FARMER".equalsIgnoreCase(role)) {
                mainApp.switchToScreen("producer.fxml");
            } else if ("SUPPLIER".equalsIgnoreCase(role) || "CONSUMER".equalsIgnoreCase(role)) {
                mainApp.switchToScreen("logistics.fxml");
            } else {
                mainApp.switchToScreen("login.fxml");
            }
        }
    }
    
    /**
     * Start auto-refresh timer for contacts
     */
    private void startAutoRefresh() {
        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    // Silently refresh contacts without disrupting UI
                    loadContacts();
                });
            }
        }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS);
        
        logger.debug("Auto-refresh started (interval: {} ms)", REFRESH_INTERVAL_MS);
    }
    
    /**
     * Stop auto-refresh timer
     */
    private void stopAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
            logger.debug("Auto-refresh stopped");
        }
    }
    
    /**
     * Show error dialog
     */
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Show info dialog
     */
    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Custom cell for contact list
     */
    private static class ContactListCell extends ListCell<Participant> {
        @Override
        protected void updateItem(Participant contact, boolean empty) {
            super.updateItem(contact, empty);
            
            if (empty || contact == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            
            // Create contact cell UI
            HBox container = new HBox(10);
            container.setPadding(new Insets(8));
            container.setAlignment(Pos.CENTER_LEFT);
            
            // Status indicator
            String indicator = contact.isOnline() ? "🟢" : "⚫";
            Label statusLabel = new Label(indicator);
            statusLabel.setStyle("-fx-font-size: 14px;");
            
            // Contact info
            VBox infoBox = new VBox(2);
            Label nameLabel = new Label(contact.getDisplayName());
            nameLabel.setFont(Font.font(null, FontWeight.BOLD, 13));
            
            Label usernameLabel = new Label("@" + contact.getUsername());
            usernameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
            
            infoBox.getChildren().addAll(nameLabel, usernameLabel);
            
            // Role badge
            Label roleLabel = new Label(contact.getUserRole());
            roleLabel.setStyle("-fx-font-size: 10px; -fx-padding: 3 8; -fx-background-color: #e3f2fd; -fx-background-radius: 10; -fx-text-fill: #1976D2;");
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            container.getChildren().addAll(statusLabel, infoBox, spacer, roleLabel);
            
            setGraphic(container);
        }
    }
}
