package de.hhu.propra.link.controllers;

import de.hhu.propra.link.entities.Link;
import de.hhu.propra.link.repositories.LinkRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.annotation.SessionScope;

import javax.validation.Valid;
import java.util.Optional;

@Controller
@SessionScope
public class LinkController {
    private final LinkRepository linkRepository;
    private String errorMessage;
    private String successMessage;
    private Link currentLink = new Link();

    public LinkController(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("links", linkRepository.findAll());
        model.addAttribute("link", currentLink);
        model.addAttribute("error", errorMessage);
        model.addAttribute("success", successMessage);
        return "index";
    }

    @PostMapping("/")
    public String newLink(@ModelAttribute @Valid Link link, BindingResult bindingResult) {
        this.currentLink = link;

        // check if url has already been abbreviated
        boolean isAbbreviated = getAbbreviationIfAbbreviated(link.getUrl()) != null;
        if(isAbbreviated) {
            String abbreviation = getAbbreviationIfAbbreviated(link.getUrl());
            FieldError error = new FieldError("link", "url", "The URL already is abbreviated with: " + abbreviation);
            bindingResult.addError(error);
        }


        // suggest an abbreviation if not set unless URL already exists
        if(!isAbbreviated && link.getAbbreviation().isEmpty()){
            String abbreviation = makeAbbreviation(new String(link.getUrl()));
            link.setAbbreviation(abbreviation);
        }

        if (bindingResult.hasErrors()) {
            return "index";
        }

        if (linkRepository.findById(link.getAbbreviation()).isEmpty()) {
            linkRepository.save(link);
            setMessages(null, "Successfully added a new short link!");
            this.currentLink = new Link();
        } else {
            setMessages("The short link already exists. Try another one.", null);
        }
        return "redirect:/";
    }

    @GetMapping("/{abbreviation}")
    public String redirectUrl(@PathVariable String abbreviation) {
        setMessages(null, null);
        Optional<Link> link = linkRepository.findById(abbreviation);
        return link.map(value -> "redirect:" + value.getUrl()).orElse("redirect:/");
    }

    @PostMapping("/{abbreviation}/delete")
    public String deleteLink(@PathVariable String abbreviation) {
        Optional<Link> link = linkRepository.findById(abbreviation);
        if (link.isPresent()) {
            linkRepository.delete(link.get());
            setMessages(null, "Successfully deleted short link");
        } else {
            setMessages("Short link could not be deleted, because it was not found in the database", null);
        }
        return "redirect:/";
    }

    /**
     * Set Error and Success Messages for the frontend
     *
     * @param errorMessage   Describe error
     * @param successMessage Send a joyful message to the user
     */
    private void setMessages(String errorMessage, String successMessage) {
        this.errorMessage = errorMessage;
        this.successMessage = successMessage;
    }

    /**
     * creates a not yet used viable abbrevation for the entered url
     * @param url The URL that is to be shortened
     * @return the abbrevation for the long URL
     */
    private String makeAbbreviation(String url){

        // removing the scheme from the URL
        // "https://example.com" -> "example.com"
        int schemelessUrlindex = url.indexOf("://");
        String schemelessUrl = url;
        if(schemelessUrlindex > 0) {
            schemelessUrl = url.substring(schemelessUrlindex + 3, url.length());
        } else
            return url; // URL possibly not correct, so just return it

        // determine a suitable abbreviation taking the paths in consideration
        // ex: "sub.example.com/path/to/index.html" -> "sbxmplpti"
        String[] domainAndPaths = schemelessUrl.split("/");
        String tmp_abbreviation = unvowelize(domainAndPaths[0]);
        for(int i = 1; i < domainAndPaths.length; i++){
            tmp_abbreviation += domainAndPaths[i].charAt(0);
        }

        // create another abbreviation if the preferred one already exists
        int i = 1;
        String abbreviation = tmp_abbreviation;
        while( ! linkRepository.findById(abbreviation).isEmpty() ){
            abbreviation = new String(tmp_abbreviation + i);
            i += 1;
        }

        return abbreviation;
    }

    /**
     * @param domain The domain name where the vowels, dots and the TLD should be removed from
     * @return the String without any vowels, dots and the TLD
     */
    private String unvowelize(String domain) {
        String unvowelized = "";

        for(int i = 0; i < domain.lastIndexOf('.'); i++){
            if(! (isVowel(domain.charAt(i)) || domain.charAt(i) == '.') ){
                unvowelized += domain.charAt(i);
            }
        }

        return unvowelized;
    }

    /**
     * returns true if entered letter is a vowel, else false.
     * a, e, i, o, u are vocals.
     * @param letter The letter to check
     * @return true if letter is a vowel, false elsewise
     */
    private boolean isVowel(char letter) {
        switch(letter){
            case 'a':
            case 'e':
            case 'i':
            case 'o':
            case 'u': return true;
            default:  return false;
        }
    }

    /**
     * returns the abbreviation if URL is already abbreviated (in the database)
     * @param url The URL to check
     * @return the abbreviation if the URL is already abbreviated, else null
     */
    private String getAbbreviationIfAbbreviated(String url) {
        for (Link iterate_link : linkRepository.findAll()) {
            if (url.equals(iterate_link.getUrl())) {
                return iterate_link.getAbbreviation();
            }
        }
        return null;
    }

}
