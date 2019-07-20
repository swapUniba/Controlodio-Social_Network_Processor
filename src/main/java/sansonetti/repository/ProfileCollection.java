package sansonetti.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import sansonetti.model.Profile;

import java.util.List;

public interface ProfileCollection extends MongoRepository<Profile, String> {
    public void deleteByScreenNameAndSource(String screenName,String source);
    public Profile findByScreenNameAndSource(String screenName, String source);
    public List<Profile> findBySourceAndConnections(String source, boolean connections);

}
