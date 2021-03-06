package datadog.examples.resources;

import datadog.examples.entities.User;
import datadog.examples.entities.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller // This means that this class is a Controller
@RequestMapping(path = "/user") // This means URL's start with /demo (after Application path)
public class DBResource {

  @Autowired // This means to get the bean called userRepository
  // Which is auto-generated by Spring, we will use it to handle the data
  private UserRepository userRepository;

  @GetMapping(path = "/add") // Map ONLY GET Requests
  public @ResponseBody String addNewUser(
      @RequestParam final String name, @RequestParam final String email) {
    // @ResponseBody means the returned String is the response, not a view name
    // @RequestParam means it is a parameter from the GET or POST request

    final User n = new User();
    n.setName(name);
    n.setEmail(email);
    userRepository.save(n);
    return "Saved";
  }

  @GetMapping(path = "/all")
  public @ResponseBody Iterable<User> getAllUsers() {
    // This returns a JSON or XML with the users
    return userRepository.findAll();
  }

  @GetMapping(path = "/get")
  public @ResponseBody User getUser(@RequestParam final int id) {
    // This returns a JSON or XML with the users
    return userRepository.findOne(id);
  }
}
