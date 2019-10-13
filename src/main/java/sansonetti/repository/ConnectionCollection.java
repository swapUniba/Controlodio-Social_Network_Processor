package sansonetti.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import sansonetti.model.Connection;

import java.util.List;

public interface ConnectionCollection extends MongoRepository<Connection, String> {
    public List<Connection> findByUsernameAndType(String username, String type);
    public void deleteByContactIDAndContactUsernameAndUsernameAndType(Long contactID, String contactUsername, String username,String type);
    public int countAllByContactIDNotNull();
    public int deleteByUsername(String username);
}
