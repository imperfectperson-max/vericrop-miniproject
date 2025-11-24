package org.vericrop.gui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.MainApp;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.dao.MessageDao;
import org.vericrop.gui.dao.UserDao;
import org.vericrop.gui.models.Message;
import org.vericrop.gui.models.User;
import org.vericrop.gui.services.AuthenticationService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the inbox/messaging screen.
 * Displays inbox and sent messages with ability to compose, read, and manage messages.
 */
public class InboxController {
    private static final Logger logger = LoggerFactory.getLogger(InboxController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
    
    @FXML private ListView<Message> messageListView;
    @FXML private Label unreadCountLabel;
    @FXML private ToggleButton inboxTab;
    @FXML private ToggleButton sentTab;
    
    @FXML private VBox emptyStatePane;
    @FXML private VBox messageDetailPane;
    
    @FXML private Label messageSubject;
    @FXML private Label messageSender;
    @FXML private Label messageRecipient;
    @FXML private Label messageSentAt;
    @FXML private TextArea messageBody;
    @FXML private Button markReadButton;
    
    private ApplicationContext appContext;
    private AuthenticationService authService;
    private MessageDao messageDao;
    private UserDao userDao;
    private Message selectedMessage;
    private boolean showingInbox = true;
    
    @FXML
    public void initialize() {
        logger.info("InboxController initialized");
        
        // Get application context
        appContext = ApplicationContext.getInstance();
        authService = appContext.getAuthenticationService();
        messageDao = appContext.getMessageDao();
        userDao = appContext.getUserDao();
        
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
        
        // Set up message list custom cell factory
        messageListView.setCellFactory(lv -> new MessageListCell());
        
        // Add selection listener
        messageListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> displayMessage(newVal)
        );
        
        // Load inbox messages
        loadInboxMessages();
    }
    
    @FXML
    private void handleTabChange() {
        if (inboxTab.isSelected()) {
            showingInbox = true;
            loadInboxMessages();
        } else if (sentTab.isSelected()) {
            showingInbox = false;
            loadSentMessages();
        }
    }
    
    @FXML
    private void handleCompose() {
        logger.info("Opening compose message dialog");
        showComposeDialog(null);
    }
    
    @FXML
    private void handleRefresh() {
        logger.info("Refreshing messages");
        if (showingInbox) {
            loadInboxMessages();
        } else {
            loadSentMessages();
        }
    }
    
    @FXML
    private void handleBack() {
        logger.info("Navigating back to main screen");
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            String role = authService.getCurrentRole();
            switch (role != null ? role.toUpperCase() : "FARMER") {
                case "FARMER":
                    mainApp.showProducerScreen();
                    break;
                case "SUPPLIER":
                    mainApp.showLogisticsScreen();
                    break;
                case "CONSUMER":
                    mainApp.showConsumerScreen();
                    break;
                case "ADMIN":
                    mainApp.showProducerScreen();
                    break;
                default:
                    mainApp.showProducerScreen();
            }
        }
    }
    
    @FXML
    private void handleReply() {
        if (selectedMessage != null) {
            logger.info("Replying to message: {}", selectedMessage.getId());
            showComposeDialog(selectedMessage);
        }
    }
    
    @FXML
    private void handleMarkAsRead() {
        if (selectedMessage != null && !selectedMessage.isRead()) {
            logger.info("Marking message as read: {}", selectedMessage.getId());
            
            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() {
                    return messageDao.markAsRead(selectedMessage.getId());
                }
                
                @Override
                protected void succeeded() {
                    if (getValue()) {
                        selectedMessage.markAsRead();
                        markReadButton.setText("Mark as Unread");
                        loadInboxMessages();
                    }
                }
            };
            new Thread(task).start();
        } else if (selectedMessage != null && selectedMessage.isRead()) {
            logger.info("Marking message as unread: {}", selectedMessage.getId());
            
            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() {
                    return messageDao.markAsUnread(selectedMessage.getId());
                }
                
                @Override
                protected void succeeded() {
                    if (getValue()) {
                        selectedMessage.setRead(false);
                        selectedMessage.setReadAt(null);
                        markReadButton.setText("Mark as Read");
                        loadInboxMessages();
                    }
                }
            };
            new Thread(task).start();
        }
    }
    
    @FXML
    private void handleDelete() {
        if (selectedMessage != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Message");
            confirm.setHeaderText("Are you sure you want to delete this message?");
            confirm.setContentText("This action cannot be undone.");
            
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                logger.info("Deleting message: {}", selectedMessage.getId());
                
                // Get current user ID
                String username = authService.getCurrentUser();
                Optional<User> userOpt = userDao.findByUsername(username);
                if (userOpt.isEmpty()) {
                    logger.error("Current user not found in database");
                    return;
                }
                
                Long userId = userOpt.get().getId();
                
                Task<Boolean> task = new Task<>() {
                    @Override
                    protected Boolean call() {
                        return messageDao.deleteMessage(selectedMessage.getId(), userId);
                    }
                    
                    @Override
                    protected void succeeded() {
                        if (getValue()) {
                            // Clear detail pane and reload list
                            clearDetailPane();
                            if (showingInbox) {
                                loadInboxMessages();
                            } else {
                                loadSentMessages();
                            }
                        }
                    }
                };
                new Thread(task).start();
            }
        }
    }
    
    /**
     * Load inbox messages in background thread
     */
    private void loadInboxMessages() {
        String username = authService.getCurrentUser();
        Optional<User> userOpt = userDao.findByUsername(username);
        if (userOpt.isEmpty()) {
            logger.error("Current user not found in database");
            return;
        }
        
        Long userId = userOpt.get().getId();
        
        Task<List<Message>> task = new Task<>() {
            @Override
            protected List<Message> call() {
                return messageDao.getInboxMessages(userId);
            }
            
            @Override
            protected void succeeded() {
                List<Message> messages = getValue();
                ObservableList<Message> observableList = FXCollections.observableArrayList(messages);
                messageListView.setItems(observableList);
                
                // Update unread count
                long unreadCount = messages.stream().filter(m -> !m.isRead()).count();
                unreadCountLabel.setText(unreadCount + " unread");
                
                logger.info("Loaded {} inbox messages ({} unread)", messages.size(), unreadCount);
            }
            
            @Override
            protected void failed() {
                logger.error("Failed to load inbox messages", getException());
            }
        };
        
        new Thread(task).start();
    }
    
    /**
     * Load sent messages in background thread
     */
    private void loadSentMessages() {
        String username = authService.getCurrentUser();
        Optional<User> userOpt = userDao.findByUsername(username);
        if (userOpt.isEmpty()) {
            logger.error("Current user not found in database");
            return;
        }
        
        Long userId = userOpt.get().getId();
        
        Task<List<Message>> task = new Task<>() {
            @Override
            protected List<Message> call() {
                return messageDao.getSentMessages(userId);
            }
            
            @Override
            protected void succeeded() {
                List<Message> messages = getValue();
                ObservableList<Message> observableList = FXCollections.observableArrayList(messages);
                messageListView.setItems(observableList);
                
                unreadCountLabel.setText(messages.size() + " sent");
                
                logger.info("Loaded {} sent messages", messages.size());
            }
            
            @Override
            protected void failed() {
                logger.error("Failed to load sent messages", getException());
            }
        };
        
        new Thread(task).start();
    }
    
    /**
     * Display selected message in detail pane
     */
    private void displayMessage(Message message) {
        if (message == null) {
            clearDetailPane();
            return;
        }
        
        selectedMessage = message;
        
        // Show detail pane, hide empty state
        emptyStatePane.setManaged(false);
        emptyStatePane.setVisible(false);
        messageDetailPane.setManaged(true);
        messageDetailPane.setVisible(true);
        
        // Populate fields
        messageSubject.setText(message.getSubject());
        messageSender.setText(message.getSenderUsername() != null ? message.getSenderUsername() : "Unknown");
        messageRecipient.setText(message.getRecipientUsername() != null ? message.getRecipientUsername() : "Unknown");
        messageSentAt.setText(message.getSentAt() != null ? message.getSentAt().format(DATE_FORMATTER) : "");
        messageBody.setText(message.getBody());
        
        // Update mark read/unread button
        if (message.isRead()) {
            markReadButton.setText("Mark as Unread");
        } else {
            markReadButton.setText("Mark as Read");
            
            // Auto-mark as read if viewing inbox message (only if currently unread)
            if (showingInbox && !message.isRead()) {
                handleMarkAsRead();
            }
        }
    }
    
    /**
     * Clear detail pane and show empty state
     */
    private void clearDetailPane() {
        selectedMessage = null;
        messageDetailPane.setManaged(false);
        messageDetailPane.setVisible(false);
        emptyStatePane.setManaged(true);
        emptyStatePane.setVisible(true);
    }
    
    /**
     * Show compose message dialog
     */
    private void showComposeDialog(Message replyTo) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(replyTo != null ? "Reply to Message" : "Compose New Message");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // Recipient selection
        Label recipientLabel = new Label("To:");
        ComboBox<User> recipientCombo = new ComboBox<>();
        
        // Load users in background
        Task<List<User>> loadUsersTask = new Task<>() {
            @Override
            protected List<User> call() {
                return userDao.findAllActive();
            }
            
            @Override
            protected void succeeded() {
                List<User> users = getValue();
                // Remove current user from list
                String currentUsername = authService.getCurrentUser();
                users.removeIf(u -> u.getUsername().equals(currentUsername));
                recipientCombo.getItems().addAll(users);
                
                // Pre-select recipient if replying
                if (replyTo != null) {
                    users.stream()
                        .filter(u -> u.getUsername().equals(replyTo.getSenderUsername()))
                        .findFirst()
                        .ifPresent(recipientCombo::setValue);
                }
            }
        };
        new Thread(loadUsersTask).start();
        
        recipientCombo.setConverter(new javafx.util.StringConverter<User>() {
            @Override
            public String toString(User user) {
                return user != null ? user.getFullName() + " (" + user.getUsername() + ")" : "";
            }
            
            @Override
            public User fromString(String string) {
                return null;
            }
        });
        recipientCombo.setPrefWidth(300);
        
        // Subject field
        Label subjectLabel = new Label("Subject:");
        TextField subjectField = new TextField();
        subjectField.setPrefWidth(300);
        if (replyTo != null) {
            String subject = replyTo.getSubject();
            subjectField.setText(subject.startsWith("Re: ") ? subject : "Re: " + subject);
        }
        
        // Message body
        Label bodyLabel = new Label("Message:");
        TextArea bodyArea = new TextArea();
        bodyArea.setPrefRowCount(10);
        bodyArea.setPrefWidth(300);
        bodyArea.setWrapText(true);
        
        // Buttons
        Button sendButton = new Button("Send");
        sendButton.setDefaultButton(true);
        Button cancelButton = new Button("Cancel");
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().addAll(sendButton, cancelButton);
        
        // Layout
        grid.add(recipientLabel, 0, 0);
        grid.add(recipientCombo, 1, 0);
        grid.add(subjectLabel, 0, 1);
        grid.add(subjectField, 1, 1);
        grid.add(bodyLabel, 0, 2);
        grid.add(bodyArea, 1, 2);
        grid.add(buttonBox, 1, 3);
        
        // Button actions
        sendButton.setOnAction(e -> {
            User recipient = recipientCombo.getValue();
            String subject = subjectField.getText().trim();
            String body = bodyArea.getText().trim();
            
            if (recipient == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "Please select a recipient");
                return;
            }
            if (subject.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Error", "Please enter a subject");
                return;
            }
            if (body.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Error", "Please enter a message");
                return;
            }
            
            // Get sender ID
            String username = authService.getCurrentUser();
            Optional<User> senderOpt = userDao.findByUsername(username);
            if (senderOpt.isEmpty()) {
                logger.error("Current user not found in database");
                return;
            }
            
            Long senderId = senderOpt.get().getId();
            Long recipientId = recipient.getId();
            
            // Send message in background
            Task<Message> sendTask = new Task<>() {
                @Override
                protected Message call() {
                    return messageDao.sendMessage(senderId, recipientId, subject, body);
                }
                
                @Override
                protected void succeeded() {
                    Message sent = getValue();
                    if (sent != null) {
                        logger.info("Message sent successfully");
                        showAlert(Alert.AlertType.INFORMATION, "Success", "Message sent successfully!");
                        dialog.close();
                        
                        // Reload sent messages if on sent tab
                        if (!showingInbox) {
                            loadSentMessages();
                        }
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Error", "Failed to send message");
                    }
                }
                
                @Override
                protected void failed() {
                    logger.error("Failed to send message", getException());
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to send message: " + getException().getMessage());
                }
            };
            new Thread(sendTask).start();
        });
        
        cancelButton.setOnAction(e -> dialog.close());
        
        Scene scene = new Scene(grid);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
    
    /**
     * Show alert dialog
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Custom list cell for messages
     */
    private class MessageListCell extends ListCell<Message> {
        @Override
        protected void updateItem(Message message, boolean empty) {
            super.updateItem(message, empty);
            
            if (empty || message == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                VBox container = new VBox(5);
                container.setPadding(new Insets(8));
                
                // Subject (bold if unread)
                Label subjectLabel = new Label(message.getSubject());
                if (!message.isRead()) {
                    subjectLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #2E8B57;");
                } else {
                    subjectLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #333;");
                }
                
                // Sender/Recipient and date
                String fromTo = showingInbox ? 
                    "From: " + (message.getSenderUsername() != null ? message.getSenderUsername() : "Unknown") :
                    "To: " + (message.getRecipientUsername() != null ? message.getRecipientUsername() : "Unknown");
                Label infoLabel = new Label(fromTo);
                infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
                
                Label dateLabel = new Label(message.getSentAt() != null ? 
                    message.getSentAt().format(DATE_FORMATTER) : "");
                dateLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");
                
                // Preview (first line of body)
                Label previewLabel = new Label(message.getBodyPreview());
                previewLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");
                previewLabel.setWrapText(true);
                
                container.getChildren().addAll(subjectLabel, infoLabel, previewLabel, dateLabel);
                
                setGraphic(container);
                
                // Background color for unread messages
                if (!message.isRead() && showingInbox) {
                    setStyle("-fx-background-color: #e3f2fd;");
                } else {
                    setStyle("");
                }
            }
        }
    }
}
