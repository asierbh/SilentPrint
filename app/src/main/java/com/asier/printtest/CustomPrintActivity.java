package com.asier.printtest;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import harmony.java.awt.Color;
import mjson.Json;

public class CustomPrintActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private static final int REQUEST_SINGIN = 1;
    private TextView txt;
    public static final String TAG = "mysupertag";
    public static final String URLBASE = "https://www.google.com/cloudprint/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_print);
        txt = (TextView) findViewById(R.id.txt);
        mAuth = FirebaseAuth.getInstance();
        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.gg_client_web_id))
                .requestEmail()
                .requestServerAuthCode(getString(R.string.gg_client_web_id))
                .requestScopes(new Scope("https://www.googleapis.com/auth/cloudprint"))
                .build();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }
        });


        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
            }
        };
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "error connecting: " + connectionResult.getErrorMessage());
        Toast.makeText(this, "error CONN", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == REQUEST_SINGIN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            Log.d(TAG,"result: "+result.toString());
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign In failed, update UI appropriately
                // ...
                Toast.makeText(this, "error: Google Sign In failed: " + result.getStatus(), Toast.LENGTH_LONG).show();
                Log.d(TAG,"result: "+result.getStatus());
            }
        }
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, REQUEST_SINGIN);
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    private void firebaseAuthWithGoogle(final GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        FirebaseUser user = task.getResult().getUser();
                        txt.setText(user.getDisplayName() + "\n" + user.getEmail());//todo
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                            Toast.makeText(CustomPrintActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                        getAccess(acct.getServerAuthCode());
                    }
                });
    }

    private void getPrinters(final String token) {
        Log.d(TAG, "TOKEN: " + token);
        String url = URLBASE + "search";
        Ion.with(this)
                .load("GET", url)
                .addHeader("Authorization", "Bearer " + token)
                .asString()
                .withResponse()
                .setCallback(new FutureCallback<Response<String>>() {
                    @Override
                    public void onCompleted(Exception e, Response<String> result) {
                        JSONObject json;
                        String printers;
                        String[] printer_info;
                        String printer_id;
                        String PDFPath;

                        Log.d(TAG, "finished " + result.getHeaders().code() + ": " +
                                result.getResult());
                        //Get printer id and show it in log
                        if (e == null) {
                            try {
                                json = new JSONObject(result.getResult());
                                printers = json.getString("printers");
                                printer_info = printers.split("\\,");
                                printer_id = printer_info[21];
                                for (int i = 0 ; i<printer_info.length ; i++){

                                    Log.d("print_info","Pos " + i + ":" + printer_info[i]);
                                }
                                printer_id = printer_id.substring(6,printer_id.length()-1);
                                Log.d("print_id", printer_id);
                                txt.setText(txt.getText()+"\nPrinter_id: " + printer_id);

                                //Create pdf to send
                                PDFPath = generatePDF("test.pdf");
                                //Print PDF
                                printPdf(PDFPath,printer_id,token);

                            } catch (JSONException e1) {
                                e1.printStackTrace();
                            }
                        }
                        if (e == null) {
                            Log.d(TAG, "nice");
                        } else {
                            Log.d(TAG, "error");
                        }
                    }
                });
    }

    private void getAccess(String code) {
        String url = "https://www.googleapis.com/oauth2/v4/token";
        Ion.with(this)
            .load("POST", url)
            .setBodyParameter("client_id", getString(R.string.gg_client_web_id))
            .setBodyParameter("client_secret", getString(R.string.gg_client_web_secret))
            .setBodyParameter("code", code)
            .setBodyParameter("grant_type", "authorization_code")
            .asString()
            .withResponse()
            .setCallback(new FutureCallback<Response<String>>() {
                @Override
                public void onCompleted(Exception e, Response<String> result) {
                    Log.d(TAG, "result of getAccess: " + result.getResult());
                    if (e == null) {
                        try {
                            JSONObject json = new JSONObject(result.getResult());
                            getPrinters(json.getString("access_token"));
                        } catch (JSONException e1) {
                            e1.printStackTrace();
                        }
                    } else {
                        Log.d(TAG, "error");
                    }
                }
            });
    }

    private String generatePDF(String name){
        String path = Environment
                .getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS) + "/mipdf/" + name;
        // Creamos el documento.
        Document documento = new Document();
        // Creamos el fichero con el nombre que deseemos.
        File f = null;
        try {
            f = crearFichero(name);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Creamos el flujo de datos de salida para el fichero donde guardaremos el pdf.
        FileOutputStream ficheroPdf = null;
        try {
            ficheroPdf = new FileOutputStream(f.getAbsolutePath());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Asociamos el flujo que acabamos de crear al documento.
        try {
            PdfWriter.getInstance(documento, ficheroPdf);
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        // Abrimos el documento.
        documento.open();

        // Añadimos un título con la fuente por defecto.
        try {
            documento.add(new Paragraph("Título 1"));
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        // Añadimos un título con una fuente personalizada.
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 28,
                Font.BOLD, Color.RED);
        try {
            documento.add(new Paragraph("Título personalizado", font));
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        // Cerramos el documento.
        documento.close();

        return path;
    }

    public static File crearFichero(String nombreFichero) throws IOException {
        File ruta = getRuta();
        File fichero = null;
        if (ruta != null)
            fichero = new File(ruta, nombreFichero);
        return fichero;
    }

    public static File getRuta() {

        // El fichero será almacenado en un directorio dentro del directorio
        String name = "mipdf";
        // Descargas
        File ruta = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            ruta = new File(
                    Environment
                            .getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS),
                    name);

            if (ruta != null) {
                if (!ruta.mkdirs()) {
                    if (!ruta.exists()) {
                        return null;
                    }
                }
            }
        } else {
        }

        return ruta;
    }

   private void printPdf(String pdfPath, String printerId, String token) {
        String url = URLBASE + "submit";
        Ion.with(this)
                .load("POST", url)
                .addHeader("Authorization", "Bearer " + token)
                .setMultipartParameter("printerid", printerId)
                .setMultipartParameter("title", "print test")
                .setMultipartParameter("ticket", getTicket())
                .setMultipartFile("content", "application/pdf", new File(pdfPath))
                .asString()
                .withResponse()
                .setCallback(new FutureCallback<Response<String>>() {
                    @Override
                    public void onCompleted(Exception e, Response<String> result) {
                        if (e == null) {
                            Log.d(TAG, "PRINT CODE: " + result.getHeaders().code() +
                                    ", RESPONSE: " + result.getResult());
                            Json j = Json.read(result.getResult());
                            if (j.at("success").asBoolean()) {
                                Toast.makeText(CustomPrintActivity.this, "Success", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(CustomPrintActivity.this, "ERROR SUCCESS", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(CustomPrintActivity.this, "ERROR NULL", Toast.LENGTH_LONG).show();
                            Log.d(TAG, e.toString());
                        }
                    }
                });
    }

    private String getTicket() {
        Json ticket = Json.object();
        Json print = Json.object();
        ticket.set("version", "1.0");

        print.set("vendor_ticket_item", Json.array());
        print.set("color", Json.object("type", "STANDARD_MONOCHROME"));
        print.set("copies", Json.object("copies", 1));

        ticket.set("print", print);
        return ticket.toString();
    }
}
