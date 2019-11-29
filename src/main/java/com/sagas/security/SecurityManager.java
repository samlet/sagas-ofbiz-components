package com.sagas.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sagas.actions.ActionRequest;
import com.sagas.actions.ActionResponse;
import com.sagas.actions.RemoteAction;
import com.sagas.generic.ValueHelper;
import com.sagas.meta.model.TaFieldValue;
import com.sagas.meta.model.TaJson;
import com.sagas.meta.model.TaStringEntries;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.security.GeneralSecurityException;

@Singleton
public class SecurityManager {
    private static final String module = SecurityManager.class.getName();
    final Algorithm algorithm = Algorithm.HMAC256("secret");
    @Inject
    private LocalDispatcher dispatcher;
    @Inject
    private GenericDelegator delegator;

    JWTVerifier verifier;
    SecurityManager(){
        this.verifier = JWT.require(algorithm)
                .withIssuer("sagas")
                // .withIssuer("auth")
                .build(); //Reusable verifier instance
    }

    /// default userLoginId is 'system'
    public String createToken(String userLoginId){
        String token = JWT.create()
                .withClaim("name", "general")
                .withClaim("user", userLoginId)
                .withIssuer("sagas")
                .withArrayClaim("array", new Integer[]{1, 2, 3})
                .sign(algorithm);
        return token;
    }

    public GenericValue verifyUser(String token) throws GeneralSecurityException {
        String userLoginId=null;
        try {
            DecodedJWT jwt = verifier.verify(token);
            userLoginId=jwt.getClaim("user").asString();
            return getUserLogin(userLoginId);
        }catch(JWTDecodeException exception){
            throw new GeneralSecurityException("Invalid token");
        } catch (GenericEntityException e) {
            throw new GeneralSecurityException("Invalid user login id "+userLoginId);
        }
    }

    public GenericValue getUserLogin(String userLoginId) throws GenericEntityException {
        GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin")
                .where("userLoginId", userLoginId).cache().queryOne();
        return userLogin;
    }

    @RemoteAction
    public ActionResponse createToken(ActionRequest request) throws InvalidProtocolBufferException {
        TaStringEntries entries=TaStringEntries.parseFrom(request.getPayload());
        JWTCreator.Builder jc=JWT.create().withIssuer("sagas");
        entries.getValuesMap().forEach(jc::withClaim);
        String token=jc.sign(algorithm);
        Debug.logImportant("create a token "+token, module);
        ByteString payload= TaFieldValue.newBuilder().setStringVal(token).build().toByteString();
        return new ActionResponse(0, payload);
    }

    @RemoteAction
    public ActionResponse verifyToken(ActionRequest request) throws InvalidProtocolBufferException, GeneralSecurityException, ClassNotFoundException, ConversionException {
        TaFieldValue token=TaFieldValue.parseFrom(request.getPayload());
        GenericValue val=verifyUser(token.getStringVal());
        TaJson payload=TaJson.newBuilder().setContent(ValueHelper.mapToJson(val)).build();
        return new ActionResponse(0, payload.toByteString());
    }
}



