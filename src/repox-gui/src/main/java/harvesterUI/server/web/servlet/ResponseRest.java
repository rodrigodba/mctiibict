package harvesterUI.server.web.servlet;

import org.dom4j.DocumentException;
import pt.utl.ist.repox.services.web.WebServices;
import pt.utl.ist.repox.services.web.impl.WebServicesImpl;
import pt.utl.ist.repox.services.web.rest.InvalidRequestException;
import pt.utl.ist.util.InvalidInputException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

/**
 * Created with IntelliJ IDEA.
 * User: Gilberto Pedrosa
 * Date: 14-12-2012
 * Time: 21:53
 * To change this template use File | Settings | File Templates.
 */
public interface ResponseRest {

    public void response(HttpServletRequest request, HttpServletResponse response, WebServices webServices) throws InvalidRequestException, IOException, DocumentException, ParseException, ClassNotFoundException, NoSuchMethodException, InvalidInputException, SQLException;

}
