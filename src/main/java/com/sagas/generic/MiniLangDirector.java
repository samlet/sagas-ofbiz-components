package com.sagas.generic;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilXml;
import org.apache.ofbiz.minilang.MiniLangException;
import org.apache.ofbiz.minilang.SimpleMethod;
import org.apache.ofbiz.minilang.method.MethodContext;
import org.apache.ofbiz.service.LocalDispatcher;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MiniLangDirector {
    private static final String module = MiniLangDirector.class.getName();
    private final boolean traceEnabled=true;

    private LocalDispatcher dispatcher;
    public MiniLangDirector(LocalDispatcher dispatcher){
        this.dispatcher=dispatcher;
    }
    private Map<String, Object> createContext() {
        return UtilMisc.toMap("locale", Locale.US, "timeZone", TimeZone.getTimeZone("GMT"));
    }

    public MethodContext createServiceMethodContext() {
        MethodContext context = new MethodContext(dispatcher.getDispatchContext(), createContext(), null);
        context.setUserLogin(dispatcher.getDelegator().makeValidValue("UserLogin", UtilMisc.toMap("userLoginId", "system")), "userLogin");
        if (traceEnabled) {
            context.setTraceOn(Debug.INFO);
        }
        return context;
    }

    public SimpleMethod createSimpleMethod(String xmlString) throws IOException, SAXException, ParserConfigurationException, MiniLangException {
        return new SimpleMethod(UtilXml.readXmlDocument(xmlString).getDocumentElement(), module);
    }

    // param source "<simple-method name=\"testCheckErrors\"><check-errors/></simple-method>"
    public String exec(String source, MethodContext context) throws ParserConfigurationException, SAXException, IOException, MiniLangException {
        SimpleMethod methodToTest = createSimpleMethod(source);
        String result = methodToTest.exec(context);
        return result;
    }

    public String exec(String source) throws ParserConfigurationException, SAXException, IOException, MiniLangException {
        MethodContext context = createServiceMethodContext();
        return exec(source, context);
    }
}
