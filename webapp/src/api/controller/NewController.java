package api.controller;

import api.JsonWrapper;
import api.utils.ResumableInfo;
import api.utils.ResumableInfoStorage;
import org.json.JSONObject;
import org.model.AutoRollbackConnection;
import org.model.DBHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.zss.model.SBook;
import org.zkoss.zss.model.sys.BookBindings;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Random;

@CrossOrigin(origins = "http://localhost:3000")

@RestController
public class NewController {

    public HashMap<String, Object> newBook(@RequestHeader("auth-token") String authToken,
                                           @RequestBody String json){
        JSONObject obj = new JSONObject(json);
        String bookName = (String) obj.get("name");
        String query = "SELECT COUNT(*) FROM books WHERE bookname = ?";
        try (AutoRollbackConnection connection = DBHandler.instance.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, bookName);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                if (rs.getInt(1) > 0)
                    return JsonWrapper.generateError("Duplicated Book Name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return JsonWrapper.generateError(e.getMessage());
        }
        SBook book = BookBindings.getBookByName(bookName);
        book.checkDBSchema();
        query = "INSERT INTO user_books VALUES (?, ?, 'owner')";
        try (AutoRollbackConnection connection = DBHandler.instance.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, authToken);
            statement.setString(2, book.getId());
            statement.execute();
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            return JsonWrapper.generateError(e.getMessage());
        }
        //template.convertAndSend(getCallbackPath(), bookName);
        return JsonWrapper.generateJson(book.getId());
    }


}
