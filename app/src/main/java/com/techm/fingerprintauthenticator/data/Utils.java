package com.techm.fingerprintauthenticator.data;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.techm.fingerprintauthenticator.ui.login.LoginActivity;

//TODO use internal storage instead of sharedpreferences
public class Utils {
    public static boolean updatePasswordInfo(Context ctx,boolean value){
        if(ctx ==null){
            return false;
        }
        SharedPreferences.Editor editor = ctx.getSharedPreferences("SP", Activity.MODE_PRIVATE).edit();
        editor.putBoolean("passwordSaved",value).apply();
        return true;
    }

    public static boolean isPasswordStored(Context ctx){
        if(ctx ==null){
            return false;
        }
        SharedPreferences sp = ctx.getSharedPreferences("SP", Activity.MODE_PRIVATE);
        return sp.getBoolean("passwordSaved",false);
    }

    public static boolean savePassword(Context ctx, String encryptedPwdStr) {
        if(ctx ==null){
            return false;
        }
        SharedPreferences.Editor editor = ctx.getSharedPreferences("SP", Activity.MODE_PRIVATE).edit();
        editor.putString("encryptedPwd",encryptedPwdStr).apply();
        return true;
    }

    public static boolean saveEncrytionIV(Context ctx, String encryptionIV) {
        if(ctx ==null){
            return false;
        }
        SharedPreferences.Editor editor = ctx.getSharedPreferences("SP", Activity.MODE_PRIVATE).edit();
        editor.putString("encryptionIV",encryptionIV).apply();
        return true;
    }

    public static String getEncryptedPwd(Context ctx) {
        if(ctx ==null){
            return null;
        }
        SharedPreferences sp = ctx.getSharedPreferences("SP", Activity.MODE_PRIVATE);
        return sp.getString("encryptedPwd",null);
    }

    public static String getEncryptedIV(Context ctx) {
        if(ctx ==null){
            return null;
        }
        SharedPreferences sp = ctx.getSharedPreferences("SP", Activity.MODE_PRIVATE);
        return sp.getString("encryptionIV",null);
    }
}