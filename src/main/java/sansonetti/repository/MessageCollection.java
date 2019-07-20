package sansonetti.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import sansonetti.model.Message;

import java.util.List;

public interface MessageCollection extends MongoRepository<Message, String> {

    public List<Message> findByFromUserAndSource(String fromUser,String source);
}