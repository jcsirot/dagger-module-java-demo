package org.chelonix.aws.appdemo;

import io.quarkiverse.resteasy.problem.HttpProblem;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.chelonix.aws.appdemo.model.TextTranslation;
import org.chelonix.aws.appdemo.ports.in.TranslateTextUseCase;
import org.chelonix.aws.appdemo.ports.out.TranslateServiceException;
import org.chelonix.aws.appdemo.resource.TextTranslationResource;

@Path("/translate")
public class TranslateHandler {

  @Inject
  private TranslateTextUseCase translateTextUseCase;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
    public Response translate(@QueryParam("q") String query, @QueryParam("source") String sourceLang,
        @QueryParam("target") String targetLang) {
    if (query == null || query.trim().isEmpty()) {
      return badRequest("Missing required parameters: q");
    } else if (targetLang == null || targetLang.trim().isEmpty()) {
      return badRequest("Missing required parameters: target");
    }
    try {
      TextTranslation translation =
          sourceLang == null ? translateTextUseCase.translate(query, targetLang)
              : translateTextUseCase.translate(query, targetLang, sourceLang);
      return ok(translation);
    } catch (TranslateServiceException te) {
      return badRequest(te);
    } catch (Exception e) {
      return internalServerError(e);
    }
  }

  private Response ok(TextTranslation message) {
    return Response.ok().entity(TextTranslationResource.from(message)).build();
  }

  private Response badRequest(Exception e) {
    return error(Status.BAD_REQUEST, e.getMessage());
  }

  private Response badRequest(String message) {
    return error(Status.BAD_REQUEST, message);
  }

  private Response internalServerError(Exception e) {
    return error(Status.INTERNAL_SERVER_ERROR, e.getMessage());
  }

  private Response error(Status status, String message) {
    return HttpProblem.valueOf(status, message).toResponse();
  }
}

