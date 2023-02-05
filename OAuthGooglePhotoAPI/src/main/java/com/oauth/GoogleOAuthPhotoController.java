package com.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;
import java.util.List;
import java.util.Map;

/**
 * This is UI Controller of the project it handles the UI and data logic
 */
@Controller
@Slf4j
public class GoogleOAuthPhotoController {


    public final String APP_AUTHORIZER;
    public final String GET_ALL_ALBUM_URI;
    public final String  GET_PHOTOS_FROM_ALBUM_URI;
    public final String LOG_OUT;

    @Autowired
    private OAuth2AuthorizedClientService oAuth2ClientService;


    public GoogleOAuthPhotoController(ApplicationContext applicationContext) {

        Environment env = applicationContext.getBean( Environment.class );

        this.GET_ALL_ALBUM_URI = env.getProperty("photolibrary.albums.uri");
        this.GET_PHOTOS_FROM_ALBUM_URI = env.getProperty("photolibrary.photo.uri");
        this.LOG_OUT = env.getProperty("photolibrary.logout.uri");
        this.APP_AUTHORIZER = env.getProperty("photolibrary.authorizer");

    }

    /**
     * Displays the Home page
     * @param authenticationToken OAuth2AuthenticationToken
     * @return View - ModelAndView
     */
    @GetMapping(value = "/")
    public ModelAndView welcomePage( OAuth2AuthenticationToken authenticationToken ){

        OAuth2User oAuth2User = authenticationToken.getPrincipal();

        log.info( "User Name: " + oAuth2User.getName() );
        log.info( "User Attributes: " + oAuth2User.getAttributes() );
        log.info( "User Authorities: " + oAuth2User.getAuthorities() );

        ModelAndView model = getModelView( oAuth2User );

        model.setViewName( "welcome" );

        return model;

    }

    /**
     *
     */
    @GetMapping(value = "/photolibrary/albums")
    public ModelAndView retriveAlbums( HttpServletRequest request, OAuth2AuthenticationToken token ){

        OAuth2User principal = token.getPrincipal();

        OAuth2AuthorizedClient client = oAuth2ClientService.loadAuthorizedClient(
                            token.getAuthorizedClientRegistrationId() , token.getName()
                    );

        log.info( "ClientRegistrationId " + token.getAuthorizedClientRegistrationId() );
        log.info( "Client Name " + token.getName() );

        if(client==null){
            return destorySessionAndRedirectToHome( request );
        }

        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add( HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue() );
        HttpEntity entity = new HttpEntity("",headers);

        ResponseEntity<Map> response = rest.exchange( this.GET_ALL_ALBUM_URI , HttpMethod.GET , entity , Map.class );

        System.out.println(response.getBody());

        List<?> album = (List<?>) response.getBody().get("albums");
        ModelAndView model = getModelView(principal);
        model.addObject( "albums" , album );
        model.setViewName("album-listing");

        return model;
    }

    @GetMapping("/photolibrary/logout")
    ModelAndView logout(@AuthenticationPrincipal OidcUser user, HttpServletRequest request, OAuth2AuthenticationToken authn) {

        // invalidate the session
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
            System.out.println("> Destroying session. While next login, the request will go to Authorization Server");
        }

        // global logout. The session in Authorization and Authentication
        // Server will be terminated
        System.out.println("> Redirect to the authorization server for a global logout");
        return new ModelAndView("redirect:" + LOG_OUT + "?id_token_hint=" + user.getIdToken().getTokenValue());

    }

    /*
     * When the user requests to see all photos of an album, then a call
     * is made to the Resource server to get the information.
     */
    @GetMapping("/photolibrary/pics")
    ModelAndView retrievePhotos(HttpServletRequest request, OAuth2AuthenticationToken authn) {

        String albumId = request.getParameter("id");
        System.out.println("> Retrieving photos from " + albumId);

        OAuth2User principal = authn.getPrincipal();
        // System.out.println(principal.getAttributes());

        // Get the Authorized cli .. Very important object
        OAuth2AuthorizedClient client = oAuth2ClientService.loadAuthorizedClient(
                            authn.getAuthorizedClientRegistrationId(),
                            authn.getName());
        if (client == null) {
            return destorySessionAndRedirectToHome(request);
        }

        System.out.println(client.getAccessToken().getTokenValue());

        // Call the resource server
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        String reqBody = String.format("""
				{
					"albumId": "%s"
				}
				""", albumId);
        HttpEntity entity = new HttpEntity(reqBody, headers);
        ResponseEntity<Map> response = restTemplate.exchange(this.GET_PHOTOS_FROM_ALBUM_URI, HttpMethod.POST, entity, Map.class);
        System.out.println(response.getBody());

        // List of photos are obtained from the resource server
        List<?> photos = (List<?>) response.getBody().get("mediaItems");

        // delegates to the view html file for display
        // photos-listing.html
        ModelAndView model = getModelView( principal );
        model.addObject("photos", photos);
        model.setViewName("photos-listing");
        return model;
    }


    private ModelAndView destorySessionAndRedirectToHome(HttpServletRequest request) {
        return null;
    }

    /**
     * Sends some basic user and setup information to the views by default.
     * The call can add more information to the model as needed.
     * Passing the user information to the view
     * @param oAuth2User
     * @return ModelAndView
     */
    private ModelAndView getModelView(OAuth2User oAuth2User) {

        ModelAndView model = new ModelAndView();

        model.addObject( "authorizer" , APP_AUTHORIZER );
        model.addObject( "first" , oAuth2User.getAttribute("given_name") );
        model.addObject( "last" , oAuth2User.getAttribute("family_name") );
        model.addObject( "email" , oAuth2User.getAttribute("email") );

        String picture = oAuth2User.getAttribute("picture" );
        if( picture == null ) {
            picture = "/images/person.svg";
        }

        model.addObject( "picture" , picture );

        return model;
    }



}
