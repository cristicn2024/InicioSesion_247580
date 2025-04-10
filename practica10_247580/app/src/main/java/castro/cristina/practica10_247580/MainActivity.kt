package castro.cristina.practica10_247580

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    object Global {
        var preferencias_compartidas = "sharedpreferences"
    }

    private lateinit var etCorreo: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnCrearCuenta: Button
    private lateinit var btnGoogleLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializar vistas
        etCorreo = findViewById(R.id.etCorreo)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btn_login)
        btnCrearCuenta = findViewById(R.id.btn_crearcuenta)
        btnGoogleLogin = findViewById(R.id.btn_google_login)

        btnLogin.setOnClickListener {
            val correo = etCorreo.text.toString()
            val pass = etPassword.text.toString()

            if (correo.isNotEmpty() && pass.isNotEmpty()) {
                login_firebase(correo, pass)
            } else {
                Toast.makeText(this, "Por favor ingresa correo y contraseña", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        btnCrearCuenta.setOnClickListener {
            val correo = etCorreo.text.toString()
            val password = etPassword.text.toString()

            if (correo.isNotEmpty() && password.isNotEmpty()) {
                // Verificar que la contraseña tenga al menos 6 caracteres (requisito de Firebase)
                if (password.length >= 6) {
                    crear_cuenta_firebase(correo, password)
                } else {
                    Toast.makeText(
                        this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(this, "Por favor ingresa correo y contraseña", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        btnGoogleLogin.setOnClickListener {
            loginGoogle()
        }

        // Verificar si ya hay una sesión abierta
        verificar_sesion_abierta()
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential
        when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential =
                            GoogleIdTokenCredential.createFrom(credential.data)
                        val credencial =
                            GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                        FirebaseAuth.getInstance().signInWithCredential(credencial)
                            .addOnCompleteListener(this) { task ->
                                if (task.isSuccessful) {
                                    val intent = Intent(applicationContext, Bienvenida::class.java)
                                    intent.putExtra("Correo", task.result.user?.email)
                                    intent.putExtra("Proveedor", "Google")
                                    startActivity(intent)
                                    guardar_sesion(task.result.user?.email.toString(), "Google")
                                    finish()
                                } else {
                                    Toast.makeText(
                                        applicationContext,
                                        "Error en la autenticación con Firebase",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    } catch (e: GoogleIdTokenParsingException) {
                        Toast.makeText(
                            applicationContext,
                            "Error al procesar el token de Google",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        applicationContext, "Tipo de credencial no esperado", Toast.LENGTH_LONG
                    ).show()
                }
            }

            else -> {
                Toast.makeText(
                    applicationContext, "Tipo de credencial no soportado", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun verificar_sesion_abierta() {
        val sesion_abierta: SharedPreferences = this.getSharedPreferences(
            Global.preferencias_compartidas, Context.MODE_PRIVATE
        )
        val correo = sesion_abierta.getString("Correo", null)
        val proveedor = sesion_abierta.getString("Proveedor", null)
        if (correo != null && proveedor != null) {
            val intent = Intent(applicationContext, Bienvenida::class.java)
            intent.putExtra("Correo", correo)
            intent.putExtra("Proveedor", proveedor)
            startActivity(intent)
            finish()
        }
    }

    private fun guardar_sesion(correo: String, proveedor: String) {
        val guardar_sesion: SharedPreferences.Editor = this.getSharedPreferences(
            Global.preferencias_compartidas, Context.MODE_PRIVATE
        ).edit()
        guardar_sesion.putString("Correo", correo)
        guardar_sesion.putString("Proveedor", proveedor)
        guardar_sesion.apply()
    }

    @SuppressLint("CoroutineCreationDuringComposition")
    fun loginGoogle() {
        val credentialManager = CredentialManager.create(this)
        val signInWithGoogleOption: GetSignInWithGoogleOption =
            GetSignInWithGoogleOption.Builder(getString(R.string.web_client)).setNonce("nonce")
                .build()

        val request: GetCredentialRequest =
            GetCredentialRequest.Builder().addCredentialOption(signInWithGoogleOption).build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity,
                )
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                Toast.makeText(
                    this@MainActivity,
                    "Error al obtener la credencial: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun login_firebase(correo: String, pass: String) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(correo, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val intent = Intent(applicationContext, Bienvenida::class.java)
                    intent.putExtra("Correo", task.result.user?.email)
                    intent.putExtra("Proveedor", "Usuario/Contraseña")
                    startActivity(intent)
                    guardar_sesion(task.result.user?.email.toString(), "Usuario/Contraseña")
                    finish()
                } else {
                    Toast.makeText(
                        applicationContext, "Usuario/Contraseña incorrecto(s)", Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun crear_cuenta_firebase(correo: String, pass: String) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(correo, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Cuenta creada exitosamente
                    Toast.makeText(
                        applicationContext, "Cuenta creada exitosamente", Toast.LENGTH_SHORT
                    ).show()

                    // Iniciar sesión automáticamente con la cuenta recién creada
                    val intent = Intent(applicationContext, Bienvenida::class.java)
                    intent.putExtra("Correo", task.result.user?.email)
                    intent.putExtra("Proveedor", "Usuario/Contraseña")
                    startActivity(intent)
                    guardar_sesion(task.result.user?.email.toString(), "Usuario/Contraseña")
                    finish()
                } else {
                    // Mostrar el error específico
                    val errorMessage = when (task.exception) {
                        is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "Ya existe una cuenta con este correo electrónico"

                        is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "La contraseña es demasiado débil"

                        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "El formato del correo electrónico no es válido"

                        else -> "Error al crear la cuenta: ${task.exception?.message}"
                    }
                    Toast.makeText(
                        applicationContext, errorMessage, Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}