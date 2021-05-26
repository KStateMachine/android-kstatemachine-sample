package ru.nsk.kstatemachinesample.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.nsk.kstatemachinesample.databinding.MainFragmentBinding

class MainFragment : Fragment() {
    private val viewModel by viewModel<MainViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return MainFragmentBinding.inflate(inflater, container, false).root
    }
}