package org.libreoffice.androidapp

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import org.libreoffice.androidapp.R
import org.libreoffice.androidapp.databinding.ActivityCollaboraBinding
import org.libreoffice.androidlib.CollaboraFragment
import org.libreoffice.androidlib.external.CollaboraWebViewBackgroundColorSupplier
import java.io.File

class CollaboraActivity : AppCompatActivity(), CollaboraWebViewBackgroundColorSupplier {

    private lateinit var binding: ActivityCollaboraBinding

    private val launcher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val document = File(cacheDir, "document")
        contentResolver.openInputStream(uri)?.use { input ->
            document.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        supportFragmentManager.commit {
            add(R.id.fragment_container, CollaboraFragment.newInstance(document))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollaboraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        launcher.launch(arrayOf("*/*"))
    }

    override fun supplyCollaboraWebViewBackgroundColorAttr(): Int {
        return android.R.attr.colorBackground
    }
}